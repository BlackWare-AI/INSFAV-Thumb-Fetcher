package com.insfav.thumbfetcher;

import static org.junit.Assert.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28, manifest=Config.NONE)
public class BrowserSessionUiTest {
    @Test public void bothMethodsOpenRefreshReadHttpOnlyAndClearPanelOnPause() {
        QueueControl.lock.set(false);
        for (String engine : new String[]{"legacy","netscape"}) {
            android.content.Context c=RuntimeEnvironment.getApplication();
            SessionState.prefs(c).edit().clear().putString("language","en").commit();
            InstagramSession.selectEngine(c,engine);
            CookieManager.getInstance().setCookie(InstagramSession.ORIGIN,"sessionid=OFFLINE_PANEL%3AFIXTURE; Secure; HttpOnly");
            var controller=Robolectric.buildActivity(InstagramLoginActivity.class).create().start().resume();
            try {
                InstagramLoginActivity activity=controller.get();
                WebView browser=ReflectionHelpers.getField(activity,"browser");assertNotNull(browser);
                assertEquals(InstagramSession.ORIGIN,Shadows.shadowOf(browser).getLastLoadedUrl());
                assertFalse(browser.getSettings().getAllowFileAccess());
                ReflectionHelpers.callInstanceMethod(activity,"showCookies");
                ReflectionHelpers.setField(activity,"savedTab",false);
                ReflectionHelpers.callInstanceMethod(activity,"refreshCookies");
                TextView cookies=ReflectionHelpers.getField(activity,"cookies");
                assertTrue(cookies.getText().toString().contains("OFFLINE_PANEL%3AFIXTURE"));
                assertFalse(cookies.isSaveEnabled());assertFalse(cookies.isTextSelectable());
                int reloads=Shadows.shadowOf(browser).getReloadInvocations();
                ReflectionHelpers.callInstanceMethod(activity,"refreshPage");
                assertEquals(reloads+1,Shadows.shadowOf(browser).getReloadInvocations());
                assertTrue((activity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0);
                activity.onBackPressed();
                assertFalse(((android.app.Dialog)ReflectionHelpers.getField(activity,"details")).isShowing());
                ReflectionHelpers.callInstanceMethod(activity,"showCookies");
                controller.pause();
                TextView paused=ReflectionHelpers.getField(activity,"cookies");assertEquals("",paused.getText().toString());
            } finally {controller.stop().destroy();}
        }
    }
}
