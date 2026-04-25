package io.innoasis.y1syncer.db.repos;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import io.innoasis.y1syncer.db.DbContract;
import io.innoasis.y1syncer.db.Y1DatabaseHelper;

public class SyncStateRepository {
    private final Y1DatabaseHelper dbHelper;

    public SyncStateRepository(Y1DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public void upsertFileState(long profileId, String protocol, String remotePath, String localPath, long fileSize,
                                long remoteModifiedTs, String lastResult, String downloadStatus) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues cv = new ContentValues();
        cv.put("profile_id", profileId);
        cv.put("protocol", protocol);
        cv.put("remote_path", remotePath);
        cv.put("local_path", localPath);
        cv.put("file_size", fileSize);
        cv.put("remote_modified_ts", remoteModifiedTs);
        cv.put("last_result", lastResult);
        cv.put("last_seen_ts", now);
        cv.put("download_status", downloadStatus);
        cv.put("updated_at", now);
        int n = db.update(DbContract.T_SYNC_STATE, cv, "profile_id=? AND remote_path=?",
                new String[]{String.valueOf(profileId), remotePath});
        if (n == 0) {
            cv.put("created_at", now);
            db.insert(DbContract.T_SYNC_STATE, null, cv);
        }
    }

    public void appendRun(long profileId, String triggerType, long startedAt, long finishedAt,
                          int totalFiles, int successFiles, int failedFiles, String reason) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("profile_id", profileId);
        cv.put("trigger_type", triggerType);
        cv.put("started_at", startedAt);
        cv.put("finished_at", finishedAt);
        cv.put("total_files", totalFiles);
        cv.put("success_files", successFiles);
        cv.put("failed_files", failedFiles);
        cv.put("reason", reason);
        db.insert(DbContract.T_SYNC_RUNS, null, cv);
    }

    public JSONArray getRecentRuns(int limit) throws JSONException {
        JSONArray arr = new JSONArray();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DbContract.T_SYNC_RUNS, null, null, null, null, null, "id DESC", String.valueOf(limit));
        try {
            while (c.moveToNext()) {
                JSONObject row = new JSONObject();
                row.put("profile_id", c.getLong(c.getColumnIndex("profile_id")));
                row.put("trigger", c.getString(c.getColumnIndex("trigger_type")));
                row.put("started_at", c.getLong(c.getColumnIndex("started_at")));
                row.put("finished_at", c.getLong(c.getColumnIndex("finished_at")));
                row.put("total_files", c.getInt(c.getColumnIndex("total_files")));
                row.put("success_files", c.getInt(c.getColumnIndex("success_files")));
                row.put("failed_files", c.getInt(c.getColumnIndex("failed_files")));
                row.put("reason", c.getString(c.getColumnIndex("reason")));
                arr.put(row);
            }
        } finally {
            c.close();
        }
        return arr;
    }

    public Map<String, CachedFileState> loadFileStatesByProfile(long profileId) {
        Map<String, CachedFileState> out = new HashMap<String, CachedFileState>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(
                DbContract.T_SYNC_STATE,
                new String[]{"remote_path", "local_path", "file_size", "remote_modified_ts", "download_status"},
                "profile_id=?",
                new String[]{String.valueOf(profileId)},
                null,
                null,
                null
        );
        try {
            while (c.moveToNext()) {
                CachedFileState s = new CachedFileState();
                s.remotePath = getStringSafe(c, "remote_path");
                s.localPath = getStringSafe(c, "local_path");
                s.fileSize = getLongSafe(c, "file_size", -1L);
                s.remoteModifiedTs = getLongSafe(c, "remote_modified_ts", -1L);
                s.downloadStatus = getStringSafe(c, "download_status");
                if (s.remotePath.length() > 0) {
                    out.put(s.remotePath, s);
                }
            }
        } finally {
            c.close();
        }
        return out;
    }

    private static String getStringSafe(Cursor c, String column) {
        int idx = c.getColumnIndex(column);
        if (idx < 0 || c.isNull(idx)) {
            return "";
        }
        String s = c.getString(idx);
        return s == null ? "" : s;
    }

    private static long getLongSafe(Cursor c, String column, long def) {
        int idx = c.getColumnIndex(column);
        if (idx < 0 || c.isNull(idx)) {
            return def;
        }
        return c.getLong(idx);
    }

    public static class CachedFileState {
        public String remotePath = "";
        public String localPath = "";
        public long fileSize = -1L;
        public long remoteModifiedTs = -1L;
        public String downloadStatus = "";
    }
}
