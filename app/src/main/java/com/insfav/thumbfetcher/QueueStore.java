package com.insfav.thumbfetcher;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

final class QueueStore extends SQLiteOpenHelper {
    QueueStore(Context context) { super(context, "queue.db", null, 2); setWriteAheadLoggingEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) { db.execSQL("CREATE TABLE accounts (username TEXT PRIMARY KEY, position INTEGER NOT NULL, status TEXT NOT NULL, code TEXT NOT NULL DEFAULT '', eligible INTEGER NOT NULL DEFAULT 1)"); }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { if (oldVersion == 1 && newVersion == 2) { db.execSQL("UPDATE accounts SET status='PENDING',code='',eligible=1"); } else throw new IllegalStateException("Unsupported queue migration"); }
    void replace(BackupParser.Result result) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            db.delete("accounts", null, null); int position = 0;
            for (String user : result.users) { ContentValues values = new ContentValues(); values.put("username", user); values.put("position", position++); values.put("status", "PENDING"); db.insertOrThrow("accounts", null, values); }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    List<String> users(boolean failedOnly) {
        ArrayList<String> users = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("accounts", new String[]{"username"}, failedOnly ? "status='FAILED'" : null, null, null, null, "position")) {
            while (cursor.moveToNext()) users.add(cursor.getString(0));
        }
        return users;
    }
    // Prefer untried profiles on a manual test; do not keep probing the same unavailable rows.
    List<String> preflightCandidates() {
        ArrayList<String> users = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT username FROM accounts WHERE status!='SKIPPED' ORDER BY CASE WHEN status='PENDING' THEN 0 ELSE 1 END,position LIMIT 5", null)) {
            while (cursor.moveToNext()) users.add(cursor.getString(0));
        }
        return users;
    }
    void mark(String user, String status, String code) {
        ContentValues values = new ContentValues(); values.put("status", status); values.put("code", code);
        getWritableDatabase().update("accounts", values, "username=?", new String[]{user});
    }
    List<String> pending() {
        ArrayList<String> users = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT username FROM accounts WHERE eligible=1 AND status='PENDING' ORDER BY position", null)) { while (cursor.moveToNext()) users.add(cursor.getString(0)); }
        return users;
    }
    List<String> retryable() {
        ArrayList<String> users = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT username FROM accounts WHERE status IN ('FAILED','SKIPPED') ORDER BY position", null)) { while (c.moveToNext()) users.add(c.getString(0)); }
        return users;
    }
    void reset(List<String> users) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try { db.execSQL("UPDATE accounts SET eligible=0"); for (String user : users) { mark(user, "PENDING", ""); ContentValues v = new ContentValues(); v.put("eligible", 1); db.update("accounts", v, "username=?", new String[]{user}); } db.setTransactionSuccessful(); } finally { db.endTransaction(); }
    }
    Map<String, Integer> failures() {
        Map<String, Integer> result = new LinkedHashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT code,COUNT(*) FROM accounts WHERE status IN ('FAILED','SKIPPED') GROUP BY code ORDER BY COUNT(*) DESC", null)) { while(c.moveToNext()) result.put(c.getString(0), c.getInt(1)); }
        return result;
    }
    Map<String, Integer> counts() {
        Map<String, Integer> result = new HashMap<>();
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT status,COUNT(*) FROM accounts GROUP BY status", null)) {
            while (cursor.moveToNext()) result.put(cursor.getString(0), cursor.getInt(1));
        }
        return result;
    }
}
