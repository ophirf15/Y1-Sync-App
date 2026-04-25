package io.innoasis.y1syncer.db.repos;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
}
