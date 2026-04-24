package io.innoasis.y1syncer.db.repos;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import io.innoasis.y1syncer.db.DbContract;
import io.innoasis.y1syncer.db.Y1DatabaseHelper;

public class UpdateBundleRepository {
    private final Y1DatabaseHelper dbHelper;

    public UpdateBundleRepository(Y1DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public void setActiveBundle(String version, String path, String checksum, String manifestUrl, String status) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.execSQL("UPDATE " + DbContract.T_UPDATE_BUNDLES + " SET is_active=0");
        ContentValues values = new ContentValues();
        long now = System.currentTimeMillis();
        values.put("resource_version", version);
        values.put("bundle_path", path);
        values.put("checksum_sha256", checksum);
        values.put("source_manifest_url", manifestUrl);
        values.put("status", status);
        values.put("is_active", 1);
        values.put("created_at", now);
        values.put("updated_at", now);
        db.insert(DbContract.T_UPDATE_BUNDLES, null, values);
    }

    public String getActiveBundlePath() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DbContract.T_UPDATE_BUNDLES, new String[]{"bundle_path"}, "is_active=1", null, null, null, "id DESC", "1");
        try {
            if (c.moveToFirst()) {
                return c.getString(c.getColumnIndex("bundle_path"));
            }
            return null;
        } finally {
            c.close();
        }
    }
}
