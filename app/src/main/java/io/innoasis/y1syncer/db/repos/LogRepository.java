package io.innoasis.y1syncer.db.repos;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.innoasis.y1syncer.db.DbContract;
import io.innoasis.y1syncer.db.Y1DatabaseHelper;

public class LogRepository {
    private static final int LIMIT = 200;
    private final Y1DatabaseHelper dbHelper;

    public LogRepository(Y1DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public void addLog(String level, String message) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("level", level);
        values.put("message", message);
        values.put("created_at", System.currentTimeMillis());
        db.insert(DbContract.T_APP_LOGS, null, values);
    }

    public JSONArray getLogsJson() throws JSONException {
        JSONArray arr = new JSONArray();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DbContract.T_APP_LOGS, null, null, null, null, null, "id DESC", String.valueOf(LIMIT));
        try {
            while (c.moveToNext()) {
                JSONObject row = new JSONObject();
                row.put("level", c.getString(c.getColumnIndex("level")));
                row.put("message", c.getString(c.getColumnIndex("message")));
                row.put("created_at", c.getLong(c.getColumnIndex("created_at")));
                arr.put(row);
            }
        } finally {
            c.close();
        }
        return arr;
    }

    public JSONArray searchMessages(String contains, int limit) throws JSONException {
        JSONArray arr = new JSONArray();
        if (contains == null || contains.length() == 0) {
            return arr;
        }
        String esc = contains.replace("%", "\\%").replace("_", "\\_");
        String like = "%" + esc + "%";
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DbContract.T_APP_LOGS, null, "message LIKE ?", new String[]{like}, null, null, "id DESC", String.valueOf(Math.max(1, limit)));
        try {
            while (c.moveToNext()) {
                JSONObject row = new JSONObject();
                row.put("level", c.getString(c.getColumnIndex("level")));
                row.put("message", c.getString(c.getColumnIndex("message")));
                row.put("created_at", c.getLong(c.getColumnIndex("created_at")));
                arr.put(row);
            }
        } finally {
            c.close();
        }
        return arr;
    }
}
