package com.insfav.thumbfetcher;

import android.app.Application;
import android.webkit.*;

/** Invalidate old HTTP-provider proof and purge browser state left by an interrupted process. */
public final class ThumbApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        android.content.SharedPreferences settings = SessionState.prefs(this);
        if (!"webview-pool-v2".equals(settings.getString("providerRevision", ""))) {
            settings.edit().putString("providerRevision", "webview-pool-v2").putString("verifiedGeneration", "")
                .putInt("verifiedProfiles", 0).putString("sessionEvidence", "UNKNOWN").putBoolean("armed", false)
                .putBoolean("userPaused", true).putString("blocked", "").putString("state", "WebView engine ready; tap Start Missing").commit();
            WorkScheduler.cancel(this);
        }
        clearBrowserSession();
    }
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level == TRIM_MEMORY_RUNNING_LOW || level == TRIM_MEMORY_RUNNING_CRITICAL || level == TRIM_MEMORY_COMPLETE) QueueControl.memoryPressure = true;
    }
    @Override public void onLowMemory() { super.onLowMemory(); QueueControl.memoryPressure = true; }
    static void clearBrowserSession() {
        try { CookieManager.getInstance().removeAllCookies(null); WebStorage.getInstance().deleteAllData(); }
        catch (RuntimeException | LinkageError ignored) { /* Provider initialization reports a fixed error when a test starts. */ }
    }
}
