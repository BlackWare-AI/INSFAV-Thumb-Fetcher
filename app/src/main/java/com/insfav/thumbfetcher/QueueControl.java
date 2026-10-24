package com.insfav.thumbfetcher;

import android.content.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class QueueControl {
    static final AtomicBoolean lock = new AtomicBoolean();
    static volatile boolean requested, memoryPressure;
    static volatile int activeViews, effectiveViews = 1, peakViews;
    static volatile int activeAccounts, peakAccounts;
    static volatile String requestedState = "Paused", current = "", phase = "Idle";
    static volatile long etaSeconds = -1;
    static volatile double completedPerMinute;
    static synchronized void webViewStarted(int active) { activeViews=active; peakViews=Math.max(peakViews,active); }
    static synchronized void webViewStopped(int active) { activeViews=Math.max(0,active); }
    static void pause(Context context, boolean stop) {
        requestedState = stop ? "Stopped" : "Paused"; requested = true;
        SharedPreferences.Editor e = SessionState.prefs(context).edit().putBoolean("userPaused", true).putString("state", requestedState);
        if (stop) e.putBoolean("armed", false); e.commit(); WorkScheduler.cancel(context);
        new Diagnostics(context).log("QUEUE", stop ? "STOP" : "PAUSE", 0);
    }
}
