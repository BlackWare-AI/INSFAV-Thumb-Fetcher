package com.insfav.thumbfetcher;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

/** Durable account queue and output-image index. Queue metadata stays in private app storage. */
final class QueueStore extends SQLiteOpenHelper {
    QueueStore(Context context) { super(context, "queue.db", null, 3); setWriteAheadLoggingEnabled(true); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE accounts (username TEXT PRIMARY KEY, position INTEGER NOT NULL, status TEXT NOT NULL, code TEXT NOT NULL DEFAULT '', eligible INTEGER NOT NULL DEFAULT 1, attempts INTEGER NOT NULL DEFAULT 0, phase TEXT NOT NULL DEFAULT 'PENDING')");
        db.execSQL("CREATE INDEX account_work_order ON accounts(status,position)");
        createCatalog(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) db.execSQL("UPDATE accounts SET status='PENDING',code='',eligible=1");
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE accounts ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE accounts ADD COLUMN phase TEXT NOT NULL DEFAULT 'PENDING'");
        }
        db.execSQL("CREATE INDEX IF NOT EXISTS account_work_order ON accounts(status,position)");
        createCatalog(db);
    }

    private static void createCatalog(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS fetch_images (username TEXT PRIMARY KEY,uri TEXT NOT NULL,name TEXT NOT NULL,size INTEGER,width INTEGER,height INTEGER,modified INTEGER,readable INTEGER NOT NULL,needs_check INTEGER NOT NULL DEFAULT 0,checked_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS fetch_image_uri ON fetch_images(uri)");
        db.execSQL("CREATE TABLE IF NOT EXISTS fetch_meta (name TEXT PRIMARY KEY,value TEXT NOT NULL)");
    }

    String meta(String name) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT value FROM fetch_meta WHERE name=?", new String[]{name})) { return c.moveToFirst() ? c.getString(0) : ""; }
    }
    void meta(String name, String value) {
        ContentValues v = new ContentValues(); v.put("name", name); v.put("value", value);
        getWritableDatabase().insertWithOnConflict("fetch_meta", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    static final class Image {
        final String user, uri, name; final long size, modified; final int width, height; final boolean readable, needsCheck;
        Image(String user, String uri, String name, long size, int width, int height, long modified, boolean readable) {
            this(user, uri, name, size, width, height, modified, readable, false);
        }
        Image(String user, String uri, String name, long size, int width, int height, long modified, boolean readable, boolean needsCheck) {
            this.user=user; this.uri=uri; this.name=name; this.size=size; this.width=width; this.height=height; this.modified=modified; this.readable=readable; this.needsCheck=needsCheck;
        }
    }
    Image image(String user) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT uri,name,size,width,height,modified,readable,needs_check FROM fetch_images WHERE username=?", new String[]{user})) {
            if (!c.moveToFirst()) return null;
            return new Image(user,c.getString(0),c.getString(1),c.getLong(2),c.getInt(3),c.getInt(4),c.getLong(5),c.getInt(6)!=0,c.getInt(7)!=0);
        }
    }
    void image(Image image) {
        ContentValues v=new ContentValues(); v.put("username",image.user); v.put("uri",image.uri); v.put("name",image.name); v.put("size",image.size);
        v.put("width",image.width); v.put("height",image.height); v.put("modified",image.modified); v.put("readable",image.readable?1:0); v.put("needs_check",image.needsCheck?1:0); v.put("checked_at",System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("fetch_images",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    void invalidateUri(String uri) {
        SQLiteDatabase db=getWritableDatabase(); db.execSQL("UPDATE fetch_images SET needs_check=1 WHERE uri=?",new Object[]{uri});
        db.execSQL("UPDATE accounts SET status='PENDING',phase='PENDING' WHERE username IN (SELECT username FROM fetch_images WHERE uri=? AND needs_check=1) AND status IN ('SUCCESS','EXISTING')",new Object[]{uri});
    }
    void settleImages() {
        getWritableDatabase().execSQL("UPDATE accounts SET status='EXISTING',phase='COMPLETED',code='LOCAL_IMAGE' WHERE username IN (SELECT username FROM fetch_images WHERE readable=1 AND needs_check=0) AND status NOT IN ('SUCCESS','EXISTING')");
    }
    void clearMissingImages(Set<String> seen){ArrayList<String> missing=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT username FROM fetch_images",null)){while(c.moveToNext())if(!seen.contains(c.getString(0)))missing.add(c.getString(0));}for(String user:missing){image(new Image(user,"",user+".webp",0,0,0,0,false));requeueLost(user);}}
    void requeueLost(String user){getWritableDatabase().execSQL("UPDATE accounts SET status='PENDING',phase='PENDING',code='',attempts=0,eligible=1 WHERE username=? AND status IN ('SUCCESS','EXISTING')",new String[]{user});}

    /** Reimporting the same backup is a no-op; changed lists retain status for unchanged usernames. */
    void replace(BackupParser.Result result) {
        LinkedHashSet<String> normalized=new LinkedHashSet<>();
        for(String raw:result.users){String user=Identity.normalize(raw);if(Identity.valid(user))normalized.add(user);}
        String signature=String.join("\n",normalized);
        if(signature.equals(meta("users")))return;
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            HashSet<String> wanted=new HashSet<>(normalized);
            for(String old:users(false))if(!wanted.contains(old))db.delete("accounts","username=?",new String[]{old});
            int position=0;
            for(String user:normalized){
                ContentValues insert=new ContentValues();insert.put("username",user);insert.put("position",position);insert.put("status","PENDING");insert.put("phase","PENDING");
                db.insertWithOnConflict("accounts",null,insert,SQLiteDatabase.CONFLICT_IGNORE);
                ContentValues order=new ContentValues();order.put("position",position++);db.update("accounts",order,"username=?",new String[]{user});
            }
            meta("users",signature);settleImages();db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    List<String> users(boolean failedOnly) {
        ArrayList<String> users=new ArrayList<>(); String where=failedOnly?"status IN ('FAILED','RETRY_LATER')":null;
        try(Cursor c=getReadableDatabase().query("accounts",new String[]{"username"},where,null,null,null,"position")){while(c.moveToNext())users.add(c.getString(0));}
        return users;
    }
    List<String> preflightCandidates() {
        ArrayList<String> users=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT username FROM accounts WHERE status!='SKIPPED' ORDER BY CASE WHEN status='PENDING' THEN 0 ELSE 1 END,position LIMIT 5",null)){while(c.moveToNext())users.add(c.getString(0));}
        return users;
    }
    void mark(String user,String status,String code) {
        ContentValues v=new ContentValues();v.put("status",status);v.put("code",code);
        v.put("phase",status.equals("SUCCESS")||status.equals("EXISTING")?"COMPLETED":status.equals("SKIPPED")?"SKIPPED":code.equals("RATE_LIMITED")?"RATE_LIMITED":status.equals("RETRY_LATER")?"PENDING":status);
        getWritableDatabase().update("accounts",v,"username=?",new String[]{user});
    }
    List<String> pending() { return select("status IN ('PENDING','RETRY_LATER') AND eligible=1"); }
    List<String> retryable() { return select("status IN ('FAILED','SKIPPED','RETRY_LATER')"); }
    private List<String> select(String where) {
        ArrayList<String> users=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("accounts",new String[]{"username"},where,null,null,null,"position")){while(c.moveToNext())users.add(c.getString(0));}
        return users;
    }
    void reset(List<String> users) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try { db.execSQL("UPDATE accounts SET eligible=0");for(String user:users){ContentValues v=new ContentValues();v.put("status","PENDING");v.put("code","");v.put("phase","PENDING");v.put("attempts",0);v.put("eligible",1);db.update("accounts",v,"username=?",new String[]{user});}db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
    }
    void recoverInterrupted(){getWritableDatabase().execSQL("UPDATE accounts SET status='PENDING',phase='PENDING',eligible=1 WHERE phase='DOWNLOADING'");}
    void prepareMissing(){getWritableDatabase().execSQL("UPDATE accounts SET status='PENDING',phase='PENDING',code='',eligible=1 WHERE status IN ('SUCCESS','EXISTING','PENDING','RETRY_LATER')");}
    void attempted(String user){getWritableDatabase().execSQL("UPDATE accounts SET attempts=attempts+1 WHERE username=?",new String[]{user});}
    boolean claim(String user){ContentValues v=new ContentValues();v.put("phase","DOWNLOADING");return getWritableDatabase().update("accounts",v,"username=? AND phase!='DOWNLOADING' AND status IN ('PENDING','RETRY_LATER')",new String[]{user})==1;}
    int attempts(String user){try(Cursor c=getReadableDatabase().rawQuery("SELECT attempts FROM accounts WHERE username=?",new String[]{user})){return c.moveToFirst()?c.getInt(0):0;}}
    Map<String,Integer> failures(){
        Map<String,Integer> out=new LinkedHashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT code,COUNT(*) FROM accounts WHERE status IN ('FAILED','RETRY_LATER') GROUP BY code ORDER BY COUNT(*) DESC",null)){while(c.moveToNext())out.put(c.getString(0),c.getInt(1));}
        return out;
    }
    Map<String,Integer> counts(){Map<String,Integer> out=new HashMap<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT status,COUNT(*) FROM accounts GROUP BY status",null)){while(c.moveToNext())out.put(c.getString(0),c.getInt(1));}return out;}
    int outOfTargetResolution(){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM fetch_images WHERE readable=1 AND NOT ((width=128 OR width=256) AND width=height)",null)){return c.moveToFirst()?c.getInt(0):0;}}
    int indexedImages(){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM fetch_images WHERE readable=1",null)){return c.moveToFirst()?c.getInt(0):0;}}
}
