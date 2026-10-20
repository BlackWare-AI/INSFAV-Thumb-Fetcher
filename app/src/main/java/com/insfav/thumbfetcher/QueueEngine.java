package com.insfav.thumbfetcher;

import android.content.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Shared serial pipeline for a user-started foreground service and bounded scheduled workers. */
final class QueueEngine {
    interface Reporter { void update(String state); }
    interface Sessions { PublicProfileProvider open() throws ProfileProvider.Failure; }
    private final Context context;
    private final SharedPreferences settings;
    private final Reporter reporter;
    private final BooleanSupplier stopped;
    private final Sessions sessions;
    private volatile PublicProfileProvider provider;
    private volatile boolean cancelled;
    private volatile String cancelledState = "Interrupted; unfinished work preserved";
    QueueEngine(Context c, Reporter r, BooleanSupplier stopped) { this(c, r, stopped, () -> new PublicProfileProvider(new CookieVault(c).load())); }
    QueueEngine(Context c, Reporter r, BooleanSupplier stopped, Sessions sessions) { context = c; settings = SessionState.prefs(c); reporter = r; this.stopped = stopped; this.sessions = sessions; }
    void cancel(String state) { cancelledState = state; cancel(); }
    void cancel() { cancelled = true; PublicProfileProvider current = provider; if (current != null) current.cancel(); }
    private boolean interrupted() { return cancelled || stopped.getAsBoolean() || QueueControl.requested || Thread.currentThread().isInterrupted(); }
    private void state(String state) { QueueControl.phase = state; settings.edit().putString("state", state).apply(); reporter.update(state); }
    private void block(String code) { SharedPreferences.Editor edit = settings.edit().putString("blocked", code); if (code.startsWith("COOKIE_")) edit.putString("verifiedGeneration", "").putInt("verifiedProfiles", 0); edit.commit(); state("Paused: " + FailureText.label(code) + " (" + code + ")"); }
    void run(String action, boolean single) {
        Diagnostics log = new Diagnostics(context); String generation = settings.getString("cookieGeneration", "");
        boolean preflight = "test".equals(action); int verified = 0, consecutive = 0, attempts = 0, cached = 0; long totalMillis = 0;
        try (QueueStore queue = new QueueStore(context)) {
            provider = sessions.open();
            if (preflight) {
                if (System.currentTimeMillis() < settings.getLong("retryAt", 0)) { block("RATE_LIMITED"); return; }
                settings.edit().putString("verifiedGeneration", "").putInt("verifiedProfiles", 0).commit();
                state("Testing cookie session"); provider.authenticate(); log.log("COOKIE", "SESSION_VERIFIED", 0);
                if (queue.users(false).size() < 3 || settings.getString("tree", "").isEmpty()) { state("Cookies Valid. Import at least 3 accounts, then Test Cookies again."); return; }
            } else if (!SessionState.verified(settings)) { block("COOKIE_PREFLIGHT_REQUIRED"); return; }
            if ("missing".equals(action) || "failed".equals(action)) queue.reset(queue.users("failed".equals(action)));
            RunPolicy policy = RunPolicy.read(settings);
            if (!preflight && !policy.contains(ZonedDateTime.now())) { state("Waiting for working hours"); return; }
            OutputFolder output = new OutputFolder(context, settings.getString("tree", ""));
            List<String> targets = preflight ? queue.users(false).subList(0, Math.min(5, queue.users(false).size())) : queue.pending();
            for (int index = 0; index < targets.size() && !interrupted(); index++) {
                String user = targets.get(index); QueueControl.current = user;
                if (!generation.equals(settings.getString("cookieGeneration", ""))) { block("COOKIE_CHANGED"); return; }
                if (!preflight && !policy.contains(ZonedDateTime.now())) { state("Waiting for working hours"); return; }
                String filename = Identity.outputFilename(user); output.recover(filename);
                if (!preflight && output.valid(filename)) { queue.mark(user, "EXISTING", "VALID_WEBP"); state("Checking existing thumbnails"); if (single && ++cached >= 128) return; continue; }
                long wait = Math.max(settings.getLong("nextRequestAt", 0), settings.getLong("retryAt", 0));
                if (single && System.currentTimeMillis() < wait) { state("Waiting for next permitted request"); return; }
                state("Waiting");
                while (!interrupted() && System.currentTimeMillis() < wait) {
                    if (!preflight && !policy.contains(ZonedDateTime.now())) { state("Waiting for working hours"); return; }
                    Thread.sleep(Math.min(250, Math.max(1, wait - System.currentTimeMillis())));
                }
                if (interrupted()) break;
                if (!preflight && !policy.contains(ZonedDateTime.now())) { state("Waiting for working hours"); return; }
                long started = android.os.SystemClock.elapsedRealtime(); state(preflight ? "Verifying profile " + (index + 1) + "/" + targets.size() : "Downloading");
                settings.edit().putLong("nextRequestAt", System.currentTimeMillis() + policy.maximum * 1000L).commit();
                try {
                    byte[] image = provider.fetch(user); byte[] bytes = ThumbnailCodec.convert(image);
                    state("Writing thumbnail"); boolean wrote = output.write(filename, bytes);
                    queue.mark(user, wrote ? "SUCCESS" : "EXISTING", "VALID_WEBP"); consecutive = 0; verified++;
                    log.log("PROFILE", wrote ? "SUCCESS" : "EXISTING", 0);
                } catch (ProfileProvider.Failure error) {
                    if (interrupted()) break;
                    queue.mark(user, "FAILED", error.code); log.log("PROFILE", error.code, error.http);
                    if (error.retryAt > 0) settings.edit().putLong("retryAt", error.retryAt).commit();
                    if (error.pause || error.code.startsWith("COOKIE_") || ++consecutive >= 3) { block(error.code); return; }
                } catch (IOException error) {
                    if (QueueControl.phase.equals("Writing thumbnail")) throw error;
                    String code = Diagnostics.imageCode(error); queue.mark(user, "FAILED", code); log.log("IMAGE", code, 0);
                    if (++consecutive >= 3) { block(code); return; }
                } finally { settings.edit().putLong("nextRequestAt", System.currentTimeMillis() + policy.delayMillis()).commit(); }
                attempts++; totalMillis += android.os.SystemClock.elapsedRealtime() - started;
                QueueControl.etaSeconds = attempts >= 3 ? (targets.size() - index - 1L) * (totalMillis / attempts + (policy.minimum + policy.maximum) * 500L) / 1000 : -1;
                if (preflight && verified >= 3) { SessionState.verified(context, generation, verified); state("Cookies Valid · 3 profiles verified. Ready for Start Missing."); log.log("COOKIE", "PREFLIGHT_VERIFIED", verified); return; }
                if (single) return;
            }
            if (interrupted()) state(QueueControl.requested ? QueueControl.requestedState : cancelledState);
            else if (preflight) { block("COOKIE_PREFLIGHT_INCOMPLETE"); log.log("COOKIE", "PREFLIGHT_INCOMPLETE", verified); }
            else if (queue.pending().isEmpty()) { settings.edit().putBoolean("armed", false).commit(); state("Completed"); }
        } catch (ProfileProvider.Failure error) { if (error.retryAt > 0) settings.edit().putLong("retryAt", error.retryAt).commit(); block(error.code); log.log("COOKIE", error.code, error.http); }
        catch (SecurityException error) { block("STORAGE_PERMISSION_LOST"); log.log("STORAGE", "PERMISSION_LOST", 0); }
        catch (IOException error) { String code = Diagnostics.storageCode(error); block(code); log.log("STORAGE", code, 0); }
        catch (InterruptedException error) { state(QueueControl.requested ? QueueControl.requestedState : cancelledState); }
        catch (RuntimeException | OutOfMemoryError | StackOverflowError error) { block("PROCESSING_ERROR"); log.log("SERVICE", "PROCESSING_ERROR", 0); }
        finally { QueueControl.current = ""; QueueControl.etaSeconds = -1; if (QueueControl.requested) state(QueueControl.requestedState); }
    }
}
