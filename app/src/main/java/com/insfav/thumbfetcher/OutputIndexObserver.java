package com.insfav.thumbfetcher;

import android.content.*;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.*;

/** Watches only the user-selected SAF tree; changes invalidate private image-index entries. */
final class OutputIndexObserver {
    private static ContentObserver observer;
    private static String observedTree="";
    private static final java.util.concurrent.ConcurrentHashMap<String,Long> ownUris=new java.util.concurrent.ConcurrentHashMap<>();
    static void own(Uri uri){if(uri!=null)ownUris.put(uri.toString(),System.currentTimeMillis()+60000);}
    private static boolean ownChange(Uri uri){Long expiry=uri==null?null:ownUris.get(uri.toString());if(expiry==null)return false;if(expiry<System.currentTimeMillis()){ownUris.remove(uri.toString(),expiry);return false;}return true;}
    static synchronized void attach(Context context,String tree) {
        if(tree==null||tree.isEmpty()||tree.equals(observedTree))return;
        if(observer!=null)try{context.getContentResolver().unregisterContentObserver(observer);}catch(RuntimeException ignored){}
        observer=null;observedTree=tree;
        try {
            observer=new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override public void onChange(boolean selfChange,Uri uri) {
                    if(uri==null||ownChange(uri))return;
                    String changed=uri.toString();
                    java.util.concurrent.CompletableFuture.runAsync(()->{
                        try(QueueStore queue=new QueueStore(context)){queue.recordOutputChange(changed);}
                        catch(RuntimeException ignored){}
                    });
                }
            };
            context.getContentResolver().registerContentObserver(Uri.parse(tree),true,observer);
        } catch(RuntimeException error) { observer=null; }
    }
    private OutputIndexObserver(){}
}
