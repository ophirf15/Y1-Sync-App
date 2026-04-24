package io.innoasis.y1syncer.db.repos;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.innoasis.y1syncer.db.DbContract;
import io.innoasis.y1syncer.db.Y1DatabaseHelper;

public class ProfileRepository {
    private final Y1DatabaseHelper dbHelper;

    public ProfileRepository(Y1DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public JSONArray listProfiles() throws JSONException {
        JSONArray arr = new JSONArray();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DbContract.T_PROFILES, null, null, null, null, null, "name ASC");
        try {
            while (c.moveToNext()) {
                JSONObject item = new JSONObject();
                item.put("id", c.getLong(c.getColumnIndex("id")));
                item.put("name", c.getString(c.getColumnIndex("name")));
                item.put("protocol", c.getString(c.getColumnIndex("protocol")));
                item.put("host", c.getString(c.getColumnIndex("host")));
                item.put("port", c.getInt(c.getColumnIndex("port")));
                item.put("remote_root_path", c.getString(c.getColumnIndex("remote_root_path")));
                item.put("local_destination", c.getString(c.getColumnIndex("local_destination")));
                item.put("is_active", c.getInt(c.getColumnIndex("is_active")) == 1);
                arr.put(item);
            }
        } finally {
            c.close();
        }
        return arr;
    }

    public void ensureDefaultProfile() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor c = db.query(DbContract.T_PROFILES, new String[]{"id"}, null, null, null, null, null);
        try {
            if (c.getCount() == 0) {
                ContentValues values = new ContentValues();
                long now = System.currentTimeMillis();
                values.put("name", "Music");
                values.put("protocol", "SMB");
                values.put("host", "192.168.1.10");
                values.put("remote_root_path", "/music");
                values.put("local_root_type", "INTERNAL");
                values.put("local_destination", "Music");
                values.put("created_at", now);
                values.put("updated_at", now);
                db.insert(DbContract.T_PROFILES, null, values);
            }
        } finally {
            c.close();
        }
    }

    public long createProfile(JSONObject payload) throws JSONException {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        long now = System.currentTimeMillis();
        values.put("name", payload.optString("name", "New Profile"));
        values.put("protocol", payload.optString("protocol", "SMB"));
        values.put("host", payload.optString("host", ""));
        values.put("port", payload.optInt("port", 445));
        values.put("share_name", payload.optString("share_name", ""));
        values.put("remote_root_path", payload.optString("remote_root_path", "/"));
        values.put("local_root_type", payload.optString("local_root_type", "INTERNAL"));
        values.put("local_destination", payload.optString("local_destination", "Music"));
        values.put("include_subfolders", payload.optBoolean("include_subfolders", true) ? 1 : 0);
        values.put("allowed_types", payload.optString("allowed_types", ""));
        values.put("charging_only", payload.optBoolean("charging_only", false) ? 1 : 0);
        values.put("battery_threshold", payload.optInt("battery_threshold", 0));
        values.put("retry_count", payload.optInt("retry_count", 2));
        values.put("timeout_sec", payload.optInt("timeout_sec", 30));
        values.put("max_parallel_downloads", payload.optInt("max_parallel_downloads", 1));
        values.put("temp_extension", payload.optString("temp_extension", ".part"));
        values.put("delete_mode", payload.optBoolean("mirror_delete", false) ? "MIRROR" : "OFF");
        values.put("is_active", payload.optBoolean("is_active", true) ? 1 : 0);
        values.put("created_at", now);
        values.put("updated_at", now);
        return db.insert(DbContract.T_PROFILES, null, values);
    }
}
