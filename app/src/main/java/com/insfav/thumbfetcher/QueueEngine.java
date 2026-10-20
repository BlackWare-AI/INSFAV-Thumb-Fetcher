package com.insfav.thumbfetcher;

import android.content.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Shared serial pipeline for a user-started foreground service and bounded scheduled workers. */
final class QueueEngine {
    interface Reporter { void update(String state); }
    interface Sessions { ProfileProvider open() throws ProfileProvider.Failure; }
    private final Context context;
    private final SharedPreferences settings;
    private final Reporter reporter;
    private final BooleanSupplier stopped;
    private final Sessions sessions;
    private volatile ProfileProvider provider;
    private volatile boolean cancelled;
    private volatile String cancelledState = "Interrupted; unfinished work preserved";
    QueueEngine(Context c, Reporter r, BooleanSupplier stopped) { this(c, r, stopped, () -> new WebViewProfileProvider(c, new CookieVault(c).load())); }
    QueueEngine(Context c, Reporter r, BooleanSupplier stopped, Sessions sessions) { context = c; settings = SessionState.prefs(c); reporter = r; this.stopped = stopped; this.sessions = sessions; }
    void cancel(String state) { cancelledState = state; cancel(); }
    void cancel() { cancelled = true; ProfileProvider current = provider; if (current != null) current.cancel(); }
    private boolean interrupted() { return cancelled || stopped.getAsBoolean() || QueueControl.requested || Thread.currentThread().isInterrupted(); }
    private void state(String state) { QueueControl.phase = state; settings.edit().putString("state", state).apply(); reporter.update(state); }
    private void block(String code) { SharedPreferences.Editor edit = settings.edit().putString("blocked", code); if (code.startsWith("COOKIE_")) edit.putString("verifiedGeneration", "").putInt("verifiedProfiles", 0); if (code.equals("COOKIE_INVALID_EXPIRED") || code.equals("COOKIE_EXPIRED") || code.equals("COOKIE_INVALID")) edit.putString("sessionEvidence", "INVALID"); edit.commit(); state("Paused: " + FailureText.label(code) + " (" + code + ")"); }
    private void singleState(QueueStore queue) {
        if (queue.pending().isEmpty()) { settings.edit().putBoolean("armed", false).commit(); state("Completed"); }
        else state("Waiting for next scheduled profile");
    }
    void run(String action, boolean single) {
        Diagnostics log = new Diagnostics(context); String generation = settings.getString("cookieGeneration", "");
        boolean one = "test_one".equals(action); boolean preflight = one || "test".equals(action); boolean sessionVerified = false; int verified = 0, consecutive = 0, attempts = 0, cached = 0; long totalMillis = 0;
        try (QueueStore queue = new QueueStore(context)) {
            provider = sessions.open();
            if (preflight) {
                if (System.currentTimeMillis() < settings.getLong("retryAt", 0)) { block("RATE_LIMITED"); return; }
                settings.edit().putString("verifiedGeneration", "").putInt("verifiedProfiles", 0).putString("sessionEvidence", "UNKNOWN").commit();
                if (queue.users(false).size() < (one ? 1 : 3) || settings.getString("tree", "").isEmpty()) { state(one ? "Select Output Folder and import at least 1 account, then test again." : "Select Output Folder and import at least 3 accounts, then Test Cookies again."); return; }
                state("Testing cookie session");
                try {
                    provider.authenticate(); sessionVerified = true;
                    SessionState.evidence(context, generation, "VERIFIED"); log.log("COOKIE", "SESSION_VERIFIED", 200);
                } catch (ProfileProvider.Failure error) {
                    if (!error.code.equals("COOKIE_AUTH_UNVERIFIED")) throw error;
                    SessionState.evidence(context, generation, "UNVERIFIED"); log.log("COOKIE", "SESSION_UNVERIFIED", error.http);
                    state("Session identity unverified; testing actual profile access");
                }
            } else if (!SessionState.verified(settings)) { block("COOKIE_PREFLIGHT_REQUIRED"); return; }
            if ("missing".equals(action) || "failed".equals(action)) queue.reset(queue.users("failed".equals(action)));
            RunPolicy policy = RunPolicy.read(settings);
            if (!preflight && !policy.contains(ZonedDateTime.now())) { state("Waiting for working hours"); return; }
            OutputFolder output = new OutputFolder(context, settings.getString("tree", ""));
            List<String> targets = preflight ? queue.preflightCandidates() : queue.pending();
            if (one && targets.size() > 1) targets = targets.subList(0, 1);
            for (int index = 0; index < targets.size() && !interrupted(); index++) {
                String user = targets.get(index); QueueControl.current = user;
                if (!generation.equals(settings.getString("cookieGeneration", ""))) { block("COOKIE_CHANGED"); return; }
                if (!preflight && !policy.contains(ZonedDateTime.now())) { state("Waiting for working hours"); return; }
                String filename = Identity.outputFilename(user); output.recover(filename);
                if (!preflight && output.valid(filename)) { queue.mark(user, "EXISTING", "VALID_WEBP"); state("Checking existing thumbnails"); if (single && ++cached >= 128) { singleState(queue); return; } continue; }
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
                    // A profile-page 404 means unavailable for this run, never proof of deletion.
                    boolean unavailable = error.http == 404 && error.code.equals("PROFILE_NOT_FOUND_OR_UNAVAILABLE") && !error.pause;
                    queue.mark(user, unavailable ? "SKIPPED" : "FAILED", error.code); log.log("PROFILE", error.code, error.http);
                    if (unavailable) consecutive = 0;
                    if (error.retryAt > 0) settings.edit().putLong("retryAt", error.retryAt).commit();
                    if (error.pause || error.code.startsWith("COOKIE_") || (!unavailable && ++consecutive >= 3)) { block(error.code); return; }
                } catch (IOException error) {
                    if (QueueControl.phase.equals("Writing thumbnail")) throw error;
                    String code = Diagnostics.imageCode(error); queue.mark(user, "FAILED", code); log.log("IMAGE", code, 0);
                    if (++consecutive >= 3) { block(code); return; }
                } finally { settings.edit().putLong("nextRequestAt", System.currentTimeMillis() + policy.delayMillis()).commit(); }
                attempts++; totalMillis += android.os.SystemClock.elapsedRealtime() - started;
                QueueControl.etaSeconds = attempts >= 3 ? (targets.size() - index - 1L) * (totalMillis / attempts + (policy.minimum + policy.maximum) * 500L) / 1000 : -1;
                if (single) { singleState(queue); return; }
            }
            if (interrupted()) state(QueueControl.requested ? QueueControl.requestedState : cancelledState);
            else if (one) { state(verified == 1 ? "One profile photo verified. Run Test Cookies for up to five profiles." : "One-profile test incomplete; see failure details."); log.log("COOKIE", "SINGLE_PROFILE_TEST", verified); }
            else if (preflight && verified >= 3) { SessionState.verified(context, generation, verified); state(sessionVerified ? "Session verified · " + verified + " profile photos verified. Ready for Start Missing." : verified + " profile photos verified · Session identity unverified. Ready for Start Missing."); log.log("COOKIE", "PREFLIGHT_VERIFIED", verified); }
            else if (preflight) { block("COOKIE_PREFLIGHT_INCOMPLETE"); log.log("COOKIE", "PREFLIGHT_INCOMPLETE", verified); }
            else if (queue.pending().isEmpty()) { settings.edit().putBoolean("armed", false).commit(); state("Completed"); }
        } catch (ProfileProvider.Failure error) { if (error.retryAt > 0) settings.edit().putLong("retryAt", error.retryAt).commit(); block(error.code); log.log("COOKIE", error.code, error.http); }
        catch (SecurityException error) { block("STORAGE_PERMISSION_LOST"); log.log("STORAGE", "PERMISSION_LOST", 0); }
        catch (IOException error) { String code = Diagnostics.storageCode(error); block(code); log.log("STORAGE", code, 0); }
        catch (InterruptedException error) { state(QueueControl.requested ? QueueControl.requestedState : cancelledState); }
        catch (RuntimeException | OutOfMemoryError | StackOverflowError error) { block("PROCESSING_ERROR"); log.log("SERVICE", "PROCESSING_ERROR", 0); }
        finally { ProfileProvider current = provider; if (current != null) current.close(); QueueControl.current = ""; QueueControl.etaSeconds = -1; if (QueueControl.requested) state(QueueControl.requestedState); }
    }
}
