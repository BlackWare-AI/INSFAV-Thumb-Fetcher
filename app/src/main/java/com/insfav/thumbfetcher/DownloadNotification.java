package com.insfav.thumbfetcher;

import android.app.*;
import android.content.*;
import java.util.*;

/** Foreground notification reflects durable queue counts and terminal queue state. */
final class DownloadNotification {
    private static long lastAt;
    private static String lastPhase="";
    static void channel(Context c) { c.getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("downloads", "Thumbnail downloads", NotificationManager.IMPORTANCE_LOW)); }

    static final class Progress {
        final int downloaded,existing,failed,skipped,pending,total,done,percent; final String status;
        Progress(Map<String,Integer> counts,String phase,String blocked) {
            downloaded=counts.getOrDefault("SUCCESS",0);existing=counts.getOrDefault("EXISTING",0);failed=counts.getOrDefault("FAILED",0);skipped=counts.getOrDefault("SKIPPED",0)+counts.getOrDefault("SKIPPED_UNAVAILABLE",0);
            pending=counts.getOrDefault("PENDING",0)+counts.getOrDefault("RETRY_LATER",0);total=downloaded+existing+failed+skipped+pending;done=total-pending;percent=total==0?0:(int)(100L*done/total);
            status=blocked.equals("RATE_LIMITED")?"Rate Limited":blocked.startsWith("COOKIE_")||blocked.startsWith("LOGIN_")?"Authentication Required":!blocked.isEmpty()?"Paused":phase.startsWith("Starting")||phase.equals("Preparing")?"Starting":phase.equals("Completed")&&pending==0?(failed>0||skipped>0?"Completed with issues":"Completed"):phase.startsWith("Paused")||phase.equals("Stopped")?"Paused":phase.startsWith("Interrupted")?"Interrupted":phase.startsWith("Waiting")?"Waiting":"Downloading";
        }
        String summary(){return String.format(Locale.ROOT,"Downloaded %d · Existing %d · Failed %d · Skipped %d · Pending %d",downloaded,existing,failed,skipped,pending);}
    }

    static Notification preparing(Context c) {
        channel(c);return new Notification.Builder(c,"downloads").setSmallIcon(R.drawable.ic_fetch).setContentTitle("INSFAV Thumb Fetcher").setContentText("STARTING · Loading saved queue and thumbnail index").setProgress(0,0,true).setOnlyAlertOnce(true).setOngoing(true).build();
    }
    static Notification create(Context c,String phase) {
        channel(c);Map<String,Integer> counts;try(QueueStore q=new QueueStore(c)){counts=q.counts();}
        Progress p=new Progress(counts,phase,SessionState.prefs(c).getString("blocked",""));
        boolean terminal=p.status.startsWith("Completed")||p.status.equals("Authentication Required")||p.status.equals("Paused")||p.status.equals("Interrupted")||p.status.equals("Rate Limited");
        PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        String line=p.status+" · "+p.done+" / "+p.total+" · "+p.percent+"%";
        String detail=line+"\n"+p.summary()+"\nActive WebViews "+QueueControl.activeViews+" / "+QueueControl.effectiveViews+" · Active accounts "+QueueControl.activeAccounts;
        if(QueueControl.etaSeconds>=0)detail+="\nETA "+QueueControl.etaSeconds+" s";
        SharedPreferences prefs=SessionState.prefs(c);
        if(p.status.equals("Rate Limited")){long until=prefs.getLong("retryAt",0);detail+="\nHTTP "+prefs.getInt("blockedHttp",0)+" · "+(until>0?"Cooldown until "+new java.text.SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(until)):"Cooldown duration unknown")+"\n"+(WorkScheduler.enabled(prefs)?"Automatic resume is scheduled; Android may defer":"Resume manually after cooldown");}
        if(p.status.equals("Authentication Required"))detail+="\n"+FailureText.label(prefs.getString("blocked","LOGIN_REQUIRED"))+" · Queue and images preserved";
        PendingIntent pause=PendingIntent.getBroadcast(c,1,new Intent(c,QueueControlReceiver.class).setAction("pause"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getBroadcast(c,2,new Intent(c,QueueControlReceiver.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder=new Notification.Builder(c,"downloads").setSmallIcon(R.drawable.ic_fetch).setContentTitle("INSFAV Thumb Fetcher").setContentText(line).setStyle(new Notification.BigTextStyle().bigText(detail)).setSubText(p.summary()).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(!terminal).setAutoCancel(terminal).setProgress(Math.max(1,p.total),p.done,false);
        if(!terminal){builder.addAction(new Notification.Action.Builder(null,"Pause",pause).build()).addAction(new Notification.Action.Builder(null,"Stop",stop).build());}
        return builder.build();
    }
    static synchronized void publish(Context c,int id,String phase) {
        long now=android.os.SystemClock.elapsedRealtime();boolean terminal=phase.equals("Completed")||phase.equals("Rate Limited")||phase.startsWith("Paused")||phase.startsWith("Interrupted")||phase.startsWith("Stopped");
        if(now-lastAt<1000&&!terminal)return;if(now-lastAt<1000&&phase.equals(lastPhase))return;lastAt=now;lastPhase=phase;
        try{c.getSystemService(NotificationManager.class).notify(id,create(c,phase));}catch(RuntimeException ignored){}
    }
    static void finish(Context c,int id,String phase) {
        try{c.getSystemService(NotificationManager.class).notify(id,create(c,phase));}catch(RuntimeException ignored){}
    }
}
