package com.insfav.thumbfetcher;

import android.app.Application;
import android.webkit.*;

/** Invalidate old HTTP-provider proof and purge browser state left by an interrupted process. */
public final class ThumbApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        android.content.SharedPreferences settings = SessionState.prefs(this);
        if (!"webview-v1".equals(settings.getString("providerRevision", ""))) {
            settings.edit().putString("providerRevision", "webview-v1").putString("verifiedGeneration", "")
                .putInt("verifiedProfiles", 0).putString("sessionEvidence", "UNKNOWN").putBoolean("armed", false)
                .putBoolean("userPaused", true).putString("blocked", "").putString("state", "WebView engine ready; run Test One Profile, then Test Cookies").commit();
            WorkScheduler.cancel(this);
        }
        clearBrowserSession();
    }
    static void clearBrowserSession() {
        try { CookieManager.getInstance().removeAllCookies(null); WebStorage.getInstance().deleteAllData(); }
        catch (RuntimeException | LinkageError ignored) { /* Provider initialization reports a fixed error when a test starts. */ }
    }
}
