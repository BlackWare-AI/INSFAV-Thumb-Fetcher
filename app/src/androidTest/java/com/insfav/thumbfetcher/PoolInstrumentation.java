package com.insfav.thumbfetcher;

import android.app.*;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.webkit.*;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Real Chromium, anonymous offline pages and decoded PNGs; never contacts Instagram. */
public final class PoolInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context c = getTargetContext();
            Bitmap bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888); bitmap.eraseColor(Color.BLUE);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes); bitmap.recycle(); byte[] png = bytes.toByteArray();
            for (int requested : new int[]{1,2,5,10,30}) {
                SessionState.prefs(c).edit().putInt("concurrentWebViews", requested).commit();
                int effective = ConcurrencyPolicy.initial(c, requested);
                WebViewProfileProvider p = new WebViewProfileProvider(c, CookieJar.parse("sessionid=TEST_SESSION; ds_user_id=123", System.currentTimeMillis()),
                    (view, url) -> {
                        String user = url.substring("https://www.instagram.com/".length()).replace("/", "");
                        String html = "<html><head><link rel='canonical' href='" + url + "'></head><body><main><header><img alt=\""+user+"'s profile picture\" src='https://fixture.cdninstagram.com/"+user+".png'></header></main></body></html>";
                        view.loadDataWithBaseURL(url, html, "text/html", "UTF-8", null);
                    }, url -> new WebResourceResponse("image/png", null, new ByteArrayInputStream(png)));
                ExecutorService workers = Executors.newFixedThreadPool(effective);
                try {
                    for (int wave=0;wave<2;wave++) {
                        List<Future<JSONObject>> futures = new ArrayList<>();
                        for(int i=0;i<effective;i++) { final String user="synthetic_"+wave+"_"+i; futures.add(workers.submit(() -> p.resolve(user, false))); }
                        for(int i=0;i<futures.size();i++) {
                            JSONObject data=futures.get(i).get(70,TimeUnit.SECONDS); String user="synthetic_"+wave+"_"+i;
                            if(!data.getString("username").equals(user) || !WebProfilePolicy.image(data,user).toString().endsWith("/"+user+".png")) throw new AssertionError("Cross-account result");
                        }
                    }
                    if(p.createdCount>effective || p.reusedCount<effective)throw new AssertionError("Pool did not reuse bounded views");
                    sendStatus(0, status("requested="+requested+" effective="+effective+" created="+p.createdCount+" reused="+p.reusedCount));
                } finally { workers.shutdownNow(); p.close(); }
            }
            result.putString("stream","POOL_MATRIX_OK\n"); finish(Activity.RESULT_OK,result);
        } catch (Throwable error) {
            result.putString("stream","POOL_MATRIX_FAILED: "+error.getClass().getSimpleName()+" "+error.getMessage()+"\n"); finish(Activity.RESULT_CANCELED,result);
        }
    }
    private Bundle status(String value) {Bundle b=new Bundle(); b.putString("stream",value+"\n"); return b;}
}
