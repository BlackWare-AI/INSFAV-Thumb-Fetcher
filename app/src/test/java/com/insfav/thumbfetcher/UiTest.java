package com.insfav.thumbfetcher;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, manifest=Config.NONE)
public class UiTest {
    @Test public void cookiesAreInlineNotAFilePickerAndEditorIsNotSaved() {
        QueueControl.lock.set(false);
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create().start().resume(); MainActivity a=controller.get();
        View root=a.getWindow().getDecorView(); Button select=(Button)find(root,"Select Cookies"); assertNotNull(select); select.performClick();
        EditText editor=findCookieEditor(root); assertNotNull(editor); assertFalse(editor.isSaveEnabled()); assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS,editor.getImportantForAutofill());
        assertTrue((a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0); editor.setText("sessionid=TEST_SESSION"); assertNull(org.robolectric.Shadows.shadowOf(a).getNextStartedActivity());
        controller.pause().stop(); assertEquals("",editor.getText().toString()); controller.destroy();
    }
    private static View find(View v,String text) {
        if(v instanceof TextView&&((TextView)v).getText().toString().equals(text))return v;
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text);if(found!=null)return found;}}return null;
    }
    private static EditText findCookieEditor(View v) {
        if(v instanceof EditText&&((EditText)v).getHint()!=null&&((EditText)v).getHint().toString().contains("Netscape"))return (EditText)v;
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){EditText result=findCookieEditor(group.getChildAt(i));if(result!=null)return result;}}return null;
    }
}
