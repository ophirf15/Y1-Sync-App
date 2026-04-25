package io.innoasis.y1syncer.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

public class Y1DatabaseHelper extends SQLiteOpenHelper {
    public Y1DatabaseHelper(Context context) {
        super(context, DbContract.DB_NAME, null, DbContract.DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE profiles (id INTEGER PRIMARY KEY, name TEXT UNIQUE, protocol TEXT NOT NULL, host TEXT NOT NULL, port INTEGER, share_name TEXT, base_path TEXT, username TEXT, password_enc TEXT, domain TEXT, remote_root_path TEXT NOT NULL, local_root_type TEXT NOT NULL, local_destination TEXT NOT NULL, include_subfolders INTEGER DEFAULT 1, allowed_types TEXT, schedule_mode TEXT DEFAULT 'MANUAL', approved_ssids TEXT, charging_only INTEGER DEFAULT 0, battery_threshold INTEGER DEFAULT 0, max_parallel_downloads INTEGER DEFAULT 1, retry_count INTEGER DEFAULT 2, timeout_sec INTEGER DEFAULT 30, delete_mode TEXT DEFAULT 'OFF', skip_hidden INTEGER DEFAULT 1, temp_extension TEXT DEFAULT '.part', is_active INTEGER DEFAULT 1, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE sync_state (id INTEGER PRIMARY KEY, profile_id INTEGER NOT NULL, protocol TEXT NOT NULL, remote_path TEXT NOT NULL, local_path TEXT NOT NULL, file_size INTEGER, remote_modified_ts INTEGER, etag TEXT, checksum TEXT, last_result TEXT, last_seen_ts INTEGER, download_status TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX idx_sync_state_profile_remote ON sync_state(profile_id, remote_path)");
        db.execSQL("CREATE TABLE sync_runs (id INTEGER PRIMARY KEY, profile_id INTEGER, trigger_type TEXT, started_at INTEGER, finished_at INTEGER, total_files INTEGER, success_files INTEGER, failed_files INTEGER, reason TEXT)");
        db.execSQL("CREATE TABLE media_index (id INTEGER PRIMARY KEY, profile_id INTEGER, file_path TEXT UNIQUE NOT NULL, artist TEXT, album TEXT, title TEXT, track_no INTEGER, disc_no INTEGER, duration_ms INTEGER, genre TEXT, year INTEGER, artwork_path TEXT, added_ts INTEGER, modified_ts INTEGER, is_orphaned INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE playlists (id INTEGER PRIMARY KEY, name TEXT UNIQUE NOT NULL, encoding TEXT DEFAULT 'UTF-8', relative_paths INTEGER DEFAULT 1, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE playlist_entries (id INTEGER PRIMARY KEY, playlist_id INTEGER NOT NULL, position INTEGER NOT NULL, media_file_path TEXT NOT NULL)");
        db.execSQL("CREATE TABLE update_bundles (id INTEGER PRIMARY KEY, resource_version TEXT NOT NULL, schema_version TEXT, bundle_path TEXT, checksum_sha256 TEXT, source_manifest_url TEXT, status TEXT NOT NULL, is_active INTEGER DEFAULT 0, notes TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE update_state (id INTEGER PRIMARY KEY, update_channel TEXT, manifest_url TEXT, last_check_ts INTEGER, last_check_result TEXT, previous_bundle_version TEXT, previous_bundle_path TEXT, updated_at INTEGER)");
        db.execSQL("CREATE TABLE app_logs (id INTEGER PRIMARY KEY, level TEXT, message TEXT NOT NULL, created_at INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS update_state (id INTEGER PRIMARY KEY, update_channel TEXT, manifest_url TEXT, last_check_ts INTEGER, last_check_result TEXT, previous_bundle_version TEXT, previous_bundle_path TEXT, updated_at INTEGER)");
        }
        if (oldVersion < 3) {
            migrateProfilesMissingColumns(db);
        }
    }

    /**
     * Older installs may have a narrower {@code profiles} table; add columns so reads/updates match current code.
     */
    private static void migrateProfilesMissingColumns(SQLiteDatabase db) {
        tryAddColumn(db, "profiles", "base_path TEXT");
        tryAddColumn(db, "profiles", "share_name TEXT");
        tryAddColumn(db, "profiles", "port INTEGER");
        tryAddColumn(db, "profiles", "username TEXT");
        tryAddColumn(db, "profiles", "password_enc TEXT");
        tryAddColumn(db, "profiles", "domain TEXT");
        tryAddColumn(db, "profiles", "approved_ssids TEXT");
        tryAddColumn(db, "profiles", "schedule_mode TEXT");
        tryAddColumn(db, "profiles", "charging_only INTEGER");
        tryAddColumn(db, "profiles", "battery_threshold INTEGER");
        tryAddColumn(db, "profiles", "max_parallel_downloads INTEGER");
        tryAddColumn(db, "profiles", "retry_count INTEGER");
        tryAddColumn(db, "profiles", "timeout_sec INTEGER");
        tryAddColumn(db, "profiles", "delete_mode TEXT");
        tryAddColumn(db, "profiles", "skip_hidden INTEGER");
        tryAddColumn(db, "profiles", "temp_extension TEXT");
        tryAddColumn(db, "profiles", "include_subfolders INTEGER");
        tryAddColumn(db, "profiles", "allowed_types TEXT");
    }

    private static void tryAddColumn(SQLiteDatabase db, String table, String columnDef) {
        try {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + columnDef);
        } catch (SQLiteException ignored) {
            // duplicate column or unsupported definition on this SQLite build
        }
    }
}
