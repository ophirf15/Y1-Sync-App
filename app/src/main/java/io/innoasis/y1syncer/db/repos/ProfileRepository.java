package io.innoasis.y1syncer.db.repos;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.innoasis.y1syncer.db.DbContract;
import io.innoasis.y1syncer.db.Y1DatabaseHelper;
import io.innoasis.y1syncer.models.SyncProfile;

public class ProfileRepository {
    private final Y1DatabaseHelper dbHelper;

    public ProfileRepository(Y1DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public JSONArray listProfiles() throws JSONException {
        JSONArray arr = new JSONArray();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DbContract.T_PROFILES, null, null, null, null, null, "updated_at DESC");
        try {
            while (c.moveToNext()) {
                arr.put(cursorToProfile(c));
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
        long now = System.currentTimeMillis();
        ContentValues values = payloadToValues(payload, now, false);
        String name = values.getAsString("name");
        if (name == null || name.trim().length() == 0) {
            values.put("name", "Profile " + now);
        }
        return db.insert(DbContract.T_PROFILES, null, values);
    }

    public JSONObject getProfile(long id) throws JSONException {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DbContract.T_PROFILES, null, "id=?", new String[]{String.valueOf(id)}, null, null, null, "1");
        try {
            if (!c.moveToFirst()) {
                return null;
            }
            return cursorToProfile(c);
        } finally {
            c.close();
        }
    }

    /**
     * Returns stored password for SMB tests only; never include this in {@link #getProfile(long)} JSON.
     */
    public String getPasswordEnc(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DbContract.T_PROFILES, new String[]{"password_enc"}, "id=?", new String[]{String.valueOf(id)}, null, null, null, "1");
        try {
            if (!c.moveToFirst()) {
                return "";
            }
            return getStringSafe(c, "password_enc");
        } finally {
            c.close();
        }
    }

    /**
     * Loads profile + stored password for sync (not for public JSON APIs).
     */
    public SyncProfile loadProfileForSync(long id) throws JSONException {
        JSONObject j = getProfile(id);
        if (j == null) {
            return null;
        }
        SyncProfile p = new SyncProfile();
        p.id = j.optLong("id", id);
        p.name = j.optString("name", "");
        p.protocol = j.optString("protocol", "SMB");
        p.host = j.optString("host", "").trim();
        p.port = optIntLooseJson(j, "port", 445);
        p.shareName = j.optString("share_name", "").trim();
        p.username = j.optString("username", "");
        p.password = getPasswordEnc(id);
        p.domain = j.optString("domain", "");
        p.remoteRootPath = j.optString("remote_root_path", "/");
        p.localDestination = j.optString("local_destination", "Music");
        p.localRootType = j.optString("local_root_type", "INTERNAL");
        p.active = j.optBoolean("is_active", true);
        p.includeSubfolders = j.optBoolean("include_subfolders", true);
        p.allowedTypesCsv = j.optString("allowed_types", "");
        p.tempExtension = j.optString("temp_extension", ".part");
        if (p.tempExtension.length() == 0) {
            p.tempExtension = ".part";
        }
        return p;
    }

    /**
     * Picks a profile id for generic "sync now" triggers: first active SMB profile, else first SMB profile.
     *
     * @return profile id or {@code 0} if none
     */
    public long pickProfileIdForTriggerSync() throws JSONException {
        JSONArray arr = listProfiles();
        long firstSmb = 0;
        long firstActiveSmb = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (!"SMB".equalsIgnoreCase(o.optString("protocol", "SMB"))) {
                continue;
            }
            long id = o.optLong("id", 0);
            if (id <= 0) {
                continue;
            }
            if (firstSmb == 0) {
                firstSmb = id;
            }
            if (o.optBoolean("is_active", true) && firstActiveSmb == 0) {
                firstActiveSmb = id;
            }
        }
        return firstActiveSmb != 0 ? firstActiveSmb : firstSmb;
    }

    public String pickProfileNameForTriggerSync() throws JSONException {
        long id = pickProfileIdForTriggerSync();
        if (id <= 0) {
            return "";
        }
        JSONObject p = getProfile(id);
        return p == null ? "" : p.optString("name", "");
    }

    private static int optIntLooseJson(JSONObject o, String key, int def) {
        if (!o.has(key)) {
            return def;
        }
        try {
            return o.getInt(key);
        } catch (JSONException e) {
            try {
                String s = o.optString(key, "").trim();
                if (s.length() == 0) {
                    return def;
                }
                return Integer.parseInt(s);
            } catch (NumberFormatException nfe) {
                return def;
            }
        }
    }

    public boolean updateProfile(long id, JSONObject payload) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = payloadToValues(payload, System.currentTimeMillis(), true);
        values.remove("created_at");
        return db.update(DbContract.T_PROFILES, values, "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean deleteProfile(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(DbContract.T_PROFILES, "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean setProfileActive(long id, boolean active) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_active", active ? 1 : 0);
        values.put("updated_at", System.currentTimeMillis());
        return db.update(DbContract.T_PROFILES, values, "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public long duplicateProfile(long id) throws JSONException {
        JSONObject original = getProfile(id);
        if (original == null) {
            return -1;
        }
        original.put("name", original.optString("name", "Profile") + " Copy");
        original.remove("id");
        return createProfile(original);
    }

    private JSONObject cursorToProfile(Cursor c) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("id", getLongSafe(c, "id", 0L));
        item.put("name", getStringSafe(c, "name"));
        item.put("protocol", getStringSafe(c, "protocol"));
        item.put("host", getStringSafe(c, "host"));
        item.put("port", getIntSafe(c, "port", 445));
        item.put("share_name", getStringSafe(c, "share_name"));
        item.put("base_path", getStringSafe(c, "base_path"));
        item.put("username", getStringSafe(c, "username"));
        item.put("domain", getStringSafe(c, "domain"));
        item.put("remote_root_path", getStringSafe(c, "remote_root_path"));
        item.put("local_root_type", getStringSafe(c, "local_root_type"));
        item.put("local_destination", getStringSafe(c, "local_destination"));
        item.put("include_subfolders", getIntSafe(c, "include_subfolders", 1) == 1);
        item.put("allowed_types", getStringSafe(c, "allowed_types"));
        item.put("schedule_mode", getStringSafe(c, "schedule_mode"));
        item.put("approved_ssids", getStringSafe(c, "approved_ssids"));
        item.put("charging_only", getIntSafe(c, "charging_only", 0) == 1);
        item.put("battery_threshold", getIntSafe(c, "battery_threshold", 0));
        item.put("max_parallel_downloads", getIntSafe(c, "max_parallel_downloads", 1));
        item.put("retry_count", getIntSafe(c, "retry_count", 2));
        item.put("timeout_sec", getIntSafe(c, "timeout_sec", 30));
        item.put("temp_extension", getStringSafe(c, "temp_extension"));
        item.put("delete_mode", getStringSafe(c, "delete_mode"));
        item.put("is_active", getIntSafe(c, "is_active", 1) == 1);
        item.put("has_password", getStringSafe(c, "password_enc").length() > 0);
        item.put("updated_at", getLongSafe(c, "updated_at", 0L));
        return item;
    }

    private static String getStringSafe(Cursor c, String column) {
        int idx = c.getColumnIndex(column);
        if (idx < 0 || c.isNull(idx)) {
            return "";
        }
        String s = c.getString(idx);
        return s == null ? "" : s;
    }

    private static int getIntSafe(Cursor c, String column, int def) {
        int idx = c.getColumnIndex(column);
        if (idx < 0 || c.isNull(idx)) {
            return def;
        }
        return c.getInt(idx);
    }

    private static long getLongSafe(Cursor c, String column, long def) {
        int idx = c.getColumnIndex(column);
        if (idx < 0 || c.isNull(idx)) {
            return def;
        }
        return c.getLong(idx);
    }

    /**
     * @param preserveBlankPassword when true (profile update), omit {@code password_enc} if JSON
     *                                contains an empty password so the stored password is not wiped
     *                                (the web UI does not reload the secret).
     */
    private ContentValues payloadToValues(JSONObject payload, long now, boolean preserveBlankPassword) {
        ContentValues values = new ContentValues();
        values.put("name", payload.optString("name", "New Profile"));
        values.put("protocol", payload.optString("protocol", "SMB"));
        values.put("host", payload.optString("host", ""));
        values.put("port", optIntLoose(payload, "port", 445));
        values.put("share_name", payload.optString("share_name", ""));
        values.put("base_path", payload.optString("base_path", ""));
        values.put("username", payload.optString("username", ""));
        if (!preserveBlankPassword) {
            values.put("password_enc", payload.optString("password", ""));
        } else if (payload.has("password")) {
            String pw = payload.optString("password", "").trim();
            if (pw.length() > 0) {
                values.put("password_enc", pw);
            }
        }
        values.put("domain", payload.optString("domain", ""));
        values.put("remote_root_path", payload.optString("remote_root_path", "/"));
        values.put("local_root_type", payload.optString("local_root_type", "INTERNAL"));
        values.put("local_destination", payload.optString("local_destination", "Music"));
        values.put("include_subfolders", payload.optBoolean("include_subfolders", true) ? 1 : 0);
        values.put("allowed_types", payload.optString("allowed_types", ""));
        values.put("schedule_mode", payload.optString("schedule_mode", "MANUAL"));
        values.put("approved_ssids", payload.optString("approved_ssids", ""));
        values.put("charging_only", payload.optBoolean("charging_only", false) ? 1 : 0);
        values.put("battery_threshold", optIntLoose(payload, "battery_threshold", 0));
        values.put("max_parallel_downloads", optIntLoose(payload, "max_parallel_downloads", 1));
        values.put("retry_count", optIntLoose(payload, "retry_count", 2));
        values.put("timeout_sec", optIntLoose(payload, "timeout_sec", 30));
        values.put("temp_extension", payload.optString("temp_extension", ".part"));
        values.put("delete_mode", payload.optBoolean("mirror_delete", false) ? "MIRROR" : payload.optString("delete_mode", "OFF"));
        values.put("is_active", payload.optBoolean("is_active", true) ? 1 : 0);
        values.put("created_at", payload.optLong("created_at", now));
        values.put("updated_at", now);
        return values;
    }

    /**
     * Web clients may send numbers as strings; {@link JSONObject#optInt(String, int)} can throw on bad types.
     */
    private static int optIntLoose(JSONObject payload, String key, int def) {
        if (!payload.has(key)) {
            return def;
        }
        try {
            return payload.getInt(key);
        } catch (JSONException e) {
            try {
                String s = payload.optString(key, "").trim();
                if (s.length() == 0) {
                    return def;
                }
                return Integer.parseInt(s);
            } catch (NumberFormatException nfe) {
                return def;
            }
        }
    }
}
