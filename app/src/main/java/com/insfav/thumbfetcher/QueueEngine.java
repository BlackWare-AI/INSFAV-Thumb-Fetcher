package com.insfav.thumbfetcher;

import android.content.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** One coordinator owns SQLite/SAF commits; bounded workers only fetch and convert. */
final class QueueEngine {
    interface Reporter { void update(String state); }
    interface Sessions { ProfileProvider open() throws ProfileProvider.Failure; }
    private final Context context;
    private final SharedPreferences settings;
    private final Reporter reporter;
    private final BooleanSupplier stopped;
    private final Sessions sessions;
    private final java.util.function.IntUnaryOperator capacity;
    private volatile ProfileProvider provider;
    private volatile boolean cancelled;
    private volatile String cancelledState = "Interrupted; unfinished work preserved";
    QueueEngine(Context c, Reporter r, BooleanSupplier stopped) { this(c, r, stopped, () -> InstagramSession.open(c)); }
    QueueEngine(Context c, Reporter r, BooleanSupplier stopped, Sessions sessions) { this(c, r, stopped, sessions, requested -> ConcurrencyPolicy.initial(c, requested)); }
    QueueEngine(Context c, Reporter r, BooleanSupplier stopped, Sessions sessions, java.util.function.IntUnaryOperator capacity) { context = c; settings = SessionState.prefs(c); reporter = r; this.stopped = stopped; this.sessions = sessions; this.capacity = capacity; }
    void cancel(String state) { cancelledState = state; cancel(); }
    void cancel() { cancelled = true; ProfileProvider p = provider; if (p != null) p.cancel(); }
    private boolean interrupted() { return cancelled || stopped.getAsBoolean() || QueueControl.requested || Thread.currentThread().isInterrupted(); }
    private long lastSignal; private String lastState="";
    private void state(String value) { long now=android.os.SystemClock.elapsedRealtime();if(value.equals(lastState)&&now-lastSignal<250)return;lastSignal=now;lastState=value;QueueControl.phase = value; settings.edit().putString("state", value).apply(); reporter.update(value); }
    private void block(String code) {
        SharedPreferences.Editor e = settings.edit().putString("blocked", code);
        if (code.startsWith("COOKIE_")) e.putString("sessionEvidence", "INVALID");
        if(code.equals("RATE_LIMITED")&&settings.getLong("retryAt",0)<=System.currentTimeMillis())e.putBoolean("armed",false).putBoolean("userPaused",true);
        e.commit(); state(code.equals("RATE_LIMITED")?"Rate Limited":"Paused: " + FailureText.label(code) + " (" + code + ")");
    }
    private static final class Result {
        final String user; byte[] bytes; ProfileProvider.Failure failure;
        Result(String user) { this.user = user; }
    }
    void run(String action, boolean single) {
        Diagnostics log = new Diagnostics(context);
        ExecutorService workers = null;
        String generation = settings.getString("cookieGeneration", "");
        boolean hoursEnded = false, blocked = false, metricsStarted = false;
        try (QueueStore queue = new QueueStore(context)) {
            queue.recoverInterrupted();
            if ("verify_index".equals(action)) { state("Starting — Verifying thumbnail index"); OutputFolder checked=new OutputFolder(context,settings.getString("tree",""),queue,true);state("Paused — Thumbnail index verified");return; }
            if (System.currentTimeMillis() < settings.getLong("retryAt", 0)) { block("RATE_LIMITED"); return; }
            if ("RATE_LIMITED".equals(settings.getString("blocked", ""))) settings.edit().putString("blocked", "").commit();
            boolean timeoutRetry = "retry_timeouts".equals(action) || ("resume".equals(action) && settings.getBoolean("timeoutRetryActive", false));
            boolean missingImageRetry = "retry_missing_images".equals(action) || ("resume".equals(action) && !timeoutRetry && settings.getBoolean("missingImageRetryActive", false));
            if ("retry_timeouts".equals(action)) {
                String runId=Long.toString(System.currentTimeMillis());
                int selected=queue.prepareTimeoutRetries(runId);
                settings.edit().putString("timeoutRetryRun",runId).putBoolean("timeoutRetryActive", selected > 0).putLong("timeoutRetryStartedAt", System.currentTimeMillis()).commit();
                log.log("QUEUE", "RETRY_TIMEOUTS_SELECTED", selected);
                if (selected == 0) { settings.edit().putBoolean("armed",false).commit(); state("Retry Timeouts — Completed"); return; }
            }
            if ("retry_missing_images".equals(action)) {
                String runId=Long.toString(System.currentTimeMillis());
                int selected=queue.prepareMissingImageRetries(runId);
                settings.edit().putString("missingImageRetryRun",runId).putBoolean("missingImageRetryActive",selected>0).putLong("missingImageRetryStartedAt",System.currentTimeMillis()).commit();
                log.log("QUEUE","RETRY_MISSING_IMAGES_SELECTED",selected);
                if(selected==0){settings.edit().putBoolean("armed",false).commit();state("Retry Missing Images — Completed");return;}
            }
            if ("missing".equals(action)) queue.prepareMissing();
            else if ("failed".equals(action)) queue.reset(queue.retryable());
            RunPolicy policy = RunPolicy.read(settings);
            if (!policy.contains(ZonedDateTime.now())) { state("Paused — Outside Schedule"); return; }
            state("Starting — Loading persistent queue and thumbnail index");
            OutputFolder output = new OutputFolder(context, settings.getString("tree", ""), queue, "verify_index".equals(action));
            List<String> targets = timeoutRetry ? queue.timeoutRetryPending() : missingImageRetry ? queue.missingImageRetryPending() : queue.normalPending();
            int limit = single ? 1 : capacity.applyAsInt(ConcurrencyPolicy.validate(settings.getInt("concurrentWebViews", 1)));
            QueueControl.effectiveViews = limit; QueueControl.peakViews = 0; QueueControl.memoryPressure = false; metricsStarted = true;
            log.log("POOL", "REQUESTED_LIMIT", settings.getInt("concurrentWebViews", 1)); log.log("POOL", "EFFECTIVE_LIMIT", limit);
            workers = Executors.newFixedThreadPool(limit);
            CompletionService<Result> completed = new ExecutorCompletionService<>(workers);
            Set<String> inFlight = new HashSet<>();
            provider = sessions.open();
            int index = 0, launches = 0;
            long started = android.os.SystemClock.elapsedRealtime(); int finished = 0;
            while (index < targets.size() || !inFlight.isEmpty()) {
                if (interrupted()) break;
                if (!generation.equals(settings.getString("cookieGeneration", ""))) { block("COOKIE_CHANGED"); blocked = true; break; }
                if (QueueControl.memoryPressure) {
                    limit = ConcurrencyPolicy.reduced(limit); QueueControl.effectiveViews = limit;
                    if (provider instanceof WebViewProfileProvider) ((WebViewProfileProvider)provider).trim(limit);
                    QueueControl.memoryPressure = false; log.log("POOL", "MEMORY_PRESSURE_LIMIT", limit);
                    android.os.Debug.MemoryInfo memory = new android.os.Debug.MemoryInfo(); android.os.Debug.getMemoryInfo(memory); log.log("MEMORY", "APP_PSS_KIB", memory.getTotalPss());
                }
                long next = Math.max(settings.getLong("nextRequestAt", 0), settings.getLong("retryAt", 0));
                if (!policy.contains(ZonedDateTime.now())) hoursEnded=true;
                if (provider.blockingFailure() == null && !hoursEnded && index < targets.size() && inFlight.size() < limit && (!single || launches == 0)) {
                    String user = targets.get(index), filename = Identity.outputFilename(user);
                    // Existing-file checks do not consume a network launch slot.
                    output.recover(filename);
                    if (output.valid(filename)) {
                    if(timeoutRetry)queue.finishTimeoutRetry(user,"EXISTING","VALID_WEBP");else if(missingImageRetry)queue.finishMissingImageRetry(user,"EXISTING","VALID_WEBP");else queue.mark(user, "EXISTING", "VALID_WEBP"); index++; state("Checking existing thumbnails"); continue;
                    }
                    if (System.currentTimeMillis() >= next) {
                        if(!queue.claim(user)){index++;continue;}index++; launches++; inFlight.add(user);QueueControl.activeAccounts=inFlight.size();QueueControl.peakAccounts=Math.max(QueueControl.peakAccounts,QueueControl.activeAccounts);
                        QueueControl.current = user;
                        // Reserve before dispatch: delay controls profile starts across ALL workers.
                        settings.edit().putLong("nextRequestAt", System.currentTimeMillis() + policy.delayMillis()).commit();
                        state("Downloading");
                        completed.submit(() -> {
                            Result result = new Result(user);
                            try { if(!RunPolicy.read(settings).contains(ZonedDateTime.now()))throw new ProfileProvider.Failure("OUTSIDE_WORKING_HOURS");result.bytes = ThumbnailCodec.convert(provider.fetch(user)); }
                            catch (ProfileProvider.Failure error) { result.failure = error; }
                            catch (IOException error) { result.failure = new ProfileProvider.Failure(Diagnostics.imageCode(error)); }
                            catch (OutOfMemoryError error) { QueueControl.memoryPressure = true; result.failure = new ProfileProvider.Failure("IMAGE_MEMORY_LIMIT"); }
                            catch (RuntimeException error) { result.failure = new ProfileProvider.Failure("PROFILE_PROCESSING_ERROR"); }
                            return result;
                        });
                    } else if (single && inFlight.isEmpty()) { state("Waiting for next permitted request"); return; }
                }
                if (inFlight.isEmpty() && (hoursEnded || index >= targets.size() || single && launches > 0)) break;
                Future<Result> ready = completed.poll(100, TimeUnit.MILLISECONDS);
                if (ready == null) continue;
                Result result = ready.get();
                if (!inFlight.remove(result.user)) continue; // A result is committed once by this coordinator.
                QueueControl.activeAccounts=inFlight.size();
                if (result.failure == null) {
                    boolean wrote = output.write(Identity.outputFilename(result.user), result.bytes);
                    result.bytes = null;
                    if(timeoutRetry)queue.finishTimeoutRetry(result.user,wrote?"SUCCESS":"EXISTING","VALID_WEBP");else if(missingImageRetry)queue.finishMissingImageRetry(result.user,wrote?"SUCCESS":"EXISTING","VALID_WEBP");else queue.mark(result.user, wrote ? "SUCCESS" : "EXISTING", "VALID_WEBP");
                    settings.edit().putString("previewUser", result.user).commit();
                    log.log("PROFILE", wrote ? "SUCCESS" : "EXISTING", 0);
                } else {
                    ProfileProvider.Failure error = result.failure;
                    if(error.code.equals("OUTSIDE_WORKING_HOURS")){hoursEnded=true;continue;}
                    if (error.code.equals("REQUEST_CANCELLED")) continue; // Leave pending for a manual resume.
                    queue.attempted(result.user);
                    String resultStatus=(timeoutRetry&&(error.code.equals("WEBVIEW_TIMEOUT")||error.retryAt>0))||(missingImageRetry&&error.retryAt>0)?"FAILED":FailurePolicy.skip(error) ? (FailurePolicy.confirmedUnavailable(error) ? "SKIPPED_UNAVAILABLE" : "SKIPPED") : error.retryAt>0?"RETRY_LATER":"FAILED";
                    if(timeoutRetry)queue.finishTimeoutRetry(result.user,resultStatus,error.code);else if(missingImageRetry)queue.finishMissingImageRetry(result.user,resultStatus,error.code);else queue.mark(result.user,resultStatus,error.code);
                    log.log("PROFILE", error.code, error.http);
                    if (error.retryAt > 0) settings.edit().putLong("retryAt", error.retryAt).putInt("blockedHttp",error.http).commit();
                    if (FailurePolicy.pause(error)) { block(error.code); blocked = true; break; }
                    if (error.code.equals("WEBVIEW_RENDERER_GONE")) QueueControl.memoryPressure = true;
                }
                settings.edit().putLong("nextRequestAt", Math.max(settings.getLong("nextRequestAt", 0), System.currentTimeMillis() + policy.delayMillis())).commit();
                finished++;long elapsed=Math.max(1,android.os.SystemClock.elapsedRealtime()-started);QueueControl.completedPerMinute=finished*60000.0/elapsed;
                QueueControl.etaSeconds = finished >= 3 ? Math.max(0, targets.size() - index + inFlight.size()) * (android.os.SystemClock.elapsedRealtime() - started) / finished / 1000 : -1;
                state(hoursEnded ? "Finishing active profiles; working hours ended" : "Downloading");
            }
            if (interrupted()) state(QueueControl.requested ? QueueControl.requestedState : cancelledState);
            else if (!blocked && hoursEnded) state("Paused — Outside Schedule");
            else if (!blocked && timeoutRetry && queue.timeoutRetryPending().isEmpty()) { settings.edit().putBoolean("timeoutRetryActive", false).putBoolean("armed",false).commit(); state("Retry Timeouts — Completed"); }
            else if (!blocked && missingImageRetry && queue.missingImageRetryPending().isEmpty()) { settings.edit().putBoolean("missingImageRetryActive",false).putBoolean("armed",false).commit();state("Retry Missing Images — Completed"); }
            else if (!blocked && queue.pending().isEmpty()) { settings.edit().putBoolean("armed", false).commit(); state("Completed"); }
            else if (!blocked) state("Waiting for next scheduled profile");
        } catch (ProfileProvider.Failure error) { if (error.retryAt > 0) settings.edit().putLong("retryAt", error.retryAt).commit(); block(error.code); log.log("COOKIE", error.code, error.http); }
        catch (SecurityException error) { block("STORAGE_PERMISSION_LOST"); log.log("STORAGE", "PERMISSION_LOST", 0); }
        catch (IOException error) { String code = Diagnostics.storageCode(error); block(code); log.log("STORAGE", code, 0); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); state(QueueControl.requested ? QueueControl.requestedState : cancelledState); }
        catch (ExecutionException | RuntimeException | OutOfMemoryError error) { block("PROCESSING_ERROR"); log.log("SERVICE", "PROCESSING_ERROR", 0); }
        finally {
            ProfileProvider current = provider; if (current != null) current.cancel();
            if (workers != null) { workers.shutdownNow(); try { workers.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); } }
            if (current != null) current.close();
            if(metricsStarted)settings.edit().putInt("peakViewsLastRun",QueueControl.peakViews).putInt("effectiveViewsLastRun",QueueControl.effectiveViews).commit();
            QueueControl.activeViews = 0; QueueControl.activeAccounts=0; QueueControl.current = ""; QueueControl.etaSeconds = -1;
            if (QueueControl.requested) state(QueueControl.requestedState);
        }
    }
}
