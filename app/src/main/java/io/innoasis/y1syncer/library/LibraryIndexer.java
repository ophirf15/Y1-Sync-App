package io.innoasis.y1syncer.library;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import io.innoasis.y1syncer.db.DbContract;
import io.innoasis.y1syncer.db.Y1DatabaseHelper;
import io.innoasis.y1syncer.db.repos.ProfileRepository;
import io.innoasis.y1syncer.models.SyncProfile;
import io.innoasis.y1syncer.storage.StorageBrowser;
import io.innoasis.y1syncer.util.MediaScanHelper;

public class LibraryIndexer {
    private final Y1DatabaseHelper dbHelper;

    public LibraryIndexer(Y1DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public void indexFile(long profileId, File file) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("profile_id", profileId);
        cv.put("file_path", file.getAbsolutePath());
        cv.put("title", file.getName());
        cv.put("added_ts", System.currentTimeMillis());
        cv.put("modified_ts", file.lastModified());
        db.insertWithOnConflict(DbContract.T_MEDIA_INDEX, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Rows for the Library UI: path, artist, album, title, profile (name).
     */
    public JSONArray queryItemsJson() throws JSONException {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT m.file_path, m.artist, m.album, m.title, IFNULL(p.name,'') "
                        + "FROM " + DbContract.T_MEDIA_INDEX + " m "
                        + "LEFT JOIN " + DbContract.T_PROFILES + " p ON m.profile_id = p.id "
                        + "ORDER BY m.added_ts DESC",
                null);
        try {
            JSONArray arr = new JSONArray();
            while (c.moveToNext()) {
                JSONObject row = new JSONObject();
                row.put("path", nullToEmpty(c.getString(0)));
                row.put("artist", c.isNull(1) ? "" : nullToEmpty(c.getString(1)));
                row.put("album", c.isNull(2) ? "" : nullToEmpty(c.getString(2)));
                row.put("title", c.isNull(3) ? "" : nullToEmpty(c.getString(3)));
                row.put("profile", nullToEmpty(c.getString(4)));
                arr.put(row);
            }
            return arr;
        } finally {
            c.close();
        }
    }

    /**
     * Walks each profile's sync destination and (re-)indexes known audio files.
     *
     * @return number of files indexed
     */
    public int rescanFromProfiles(Context context, ProfileRepository profiles, StorageBrowser storage) throws JSONException, IOException {
        int total = 0;
        JSONArray list = profiles.listProfiles();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.getJSONObject(i);
            long id = o.optLong("id", 0);
            if (id <= 0) {
                continue;
            }
            SyncProfile p = profiles.loadProfileForSync(id);
            if (p == null) {
                continue;
            }
            try {
                File root = storage.resolveSyncDestinationDirectory(p.localRootType, p.localDestination);
                total += indexTree(context, id, root);
            } catch (IOException ignored) {
                // destination may not exist yet
            }
        }
        return total;
    }

    private int indexTree(Context context, long profileId, File dir) {
        if (dir == null || !dir.isDirectory()) {
            return 0;
        }
        int n = 0;
        File[] kids = dir.listFiles();
        if (kids == null) {
            return 0;
        }
        for (File f : kids) {
            if (f.isDirectory()) {
                n += indexTree(context, profileId, f);
            } else if (f.isFile() && isAudioFile(f)) {
                indexFile(profileId, f);
                MediaScanHelper.scanFile(context, f);
                n++;
            }
        }
        return n;
    }

    private static boolean isAudioFile(File f) {
        String n = f.getName().toLowerCase(Locale.US);
        return n.endsWith(".mp3") || n.endsWith(".flac") || n.endsWith(".m4a") || n.endsWith(".ogg")
                || n.endsWith(".wav") || n.endsWith(".aac") || n.endsWith(".opus");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
