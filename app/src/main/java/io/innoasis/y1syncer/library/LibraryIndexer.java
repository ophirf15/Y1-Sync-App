package io.innoasis.y1syncer.library;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaMetadataRetriever;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import io.innoasis.y1syncer.db.DbContract;
import io.innoasis.y1syncer.db.Y1DatabaseHelper;
import io.innoasis.y1syncer.db.repos.ProfileRepository;
import io.innoasis.y1syncer.models.SyncProfile;
import io.innoasis.y1syncer.storage.StorageBrowser;
import io.innoasis.y1syncer.util.MediaScanHelper;

public class LibraryIndexer {
    private final Y1DatabaseHelper dbHelper;
    public static final long LARGE_FILE_METADATA_THRESHOLD_BYTES = 128L * 1024L * 1024L;

    public LibraryIndexer(Y1DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public void indexFile(long profileId, File file) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = buildContentValues(profileId, file);
        db.insertWithOnConflict(DbContract.T_MEDIA_INDEX, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Lightweight index path used for very large files to reduce memory pressure
     * from full metadata extraction while keeping the file visible in library.
     */
    public void indexFileLightweight(long profileId, File file) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("profile_id", profileId);
        cv.put("file_path", file.getAbsolutePath());
        cv.put("artist", "");
        cv.put("album", "");
        cv.put("title", file.getName());
        cv.put("track_no", 0);
        cv.put("duration_ms", 0);
        cv.put("year", 0);
        cv.put("added_ts", System.currentTimeMillis());
        cv.put("modified_ts", file.lastModified());
        db.insertWithOnConflict(DbContract.T_MEDIA_INDEX, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static ContentValues buildContentValues(long profileId, File file) {
        AudioMetadataReader.Tags tags = AudioMetadataReader.read(file);
        ContentValues cv = new ContentValues();
        cv.put("profile_id", profileId);
        cv.put("file_path", file.getAbsolutePath());
        cv.put("artist", tags.artist);
        cv.put("album", tags.album);
        cv.put("title", tags.title);
        cv.put("track_no", tags.trackNo);
        cv.put("duration_ms", tags.durationMs);
        cv.put("year", tags.year);
        cv.put("added_ts", System.currentTimeMillis());
        cv.put("modified_ts", file.lastModified());
        return cv;
    }

    /**
     * @param queryParams keys: sort (added_ts|title|artist|album|path), order (asc|desc), q (search)
     */
    public JSONArray queryItemsJson(Map<String, String> queryParams) throws JSONException {
        String sortKey = queryParams == null ? "added_ts" : safeStr(queryParams.get("sort"), "added_ts");
        String order = queryParams == null ? "desc" : safeStr(queryParams.get("order"), "desc").toLowerCase(Locale.US);
        String q = queryParams == null ? "" : safeStr(queryParams.get("q"), "").trim();

        String col = mapSortColumn(sortKey);
        if (!"asc".equals(order) && !"desc".equals(order)) {
            order = "desc";
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT m.id, m.file_path, m.artist, m.album, m.title, IFNULL(p.name,''), IFNULL(m.duration_ms,0) ");
        sql.append("FROM ").append(DbContract.T_MEDIA_INDEX).append(" m ");
        sql.append("LEFT JOIN ").append(DbContract.T_PROFILES).append(" p ON m.profile_id = p.id ");
        String[] args = null;
        if (q.length() > 0) {
            sql.append("WHERE (m.file_path LIKE ? OR m.title LIKE ? OR m.artist LIKE ? OR m.album LIKE ?) ");
            String like = "%" + q.replace("%", "\\%").replace("_", "\\_") + "%";
            args = new String[]{like, like, like, like};
        }
        sql.append("ORDER BY ").append(col).append(" ").append("asc".equals(order) ? "ASC" : "DESC");

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = args == null ? db.rawQuery(sql.toString(), null) : db.rawQuery(sql.toString(), args);
        try {
            JSONArray arr = new JSONArray();
            while (c.moveToNext()) {
                JSONObject row = new JSONObject();
                row.put("id", c.getLong(0));
                row.put("path", nullToEmpty(c.getString(1)));
                row.put("artist", c.isNull(2) ? "" : nullToEmpty(c.getString(2)));
                row.put("album", c.isNull(3) ? "" : nullToEmpty(c.getString(3)));
                row.put("title", c.isNull(4) ? "" : nullToEmpty(c.getString(4)));
                row.put("profile", nullToEmpty(c.getString(5)));
                int durationMs = c.isNull(6) ? 0 : c.getInt(6);
                row.put("duration_ms", durationMs);
                row.put("modified_ts", 0L);
                try {
                    File f = new File(row.optString("path", ""));
                    row.put("size_bytes", (f.exists() && f.isFile()) ? f.length() : 0L);
                } catch (Exception ignored) {
                    row.put("size_bytes", 0L);
                }
                arr.put(row);
            }
            return arr;
        } finally {
            c.close();
        }
    }

    /**
     * Detect likely duplicate tracks by normalized metadata key:
     * lower(title) + lower(artist) + duration_ms.
     */
    public JSONArray queryDuplicateGroupsJson() throws JSONException {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String groupsSql =
                "SELECT " +
                        "LOWER(TRIM(IFNULL(title,''))) AS t, " +
                        "LOWER(TRIM(IFNULL(artist,''))) AS a, " +
                        "duration_ms AS d, " +
                        "COUNT(*) AS c " +
                "FROM " + DbContract.T_MEDIA_INDEX + " " +
                "WHERE LENGTH(TRIM(IFNULL(title,''))) > 0 " +
                "AND duration_ms > 0 " +
                "GROUP BY t, a, d " +
                "HAVING COUNT(*) > 1 " +
                "ORDER BY c DESC, t ASC";
        Cursor g = db.rawQuery(groupsSql, null);
        JSONArray out = new JSONArray();
        try {
            while (g.moveToNext()) {
                String t = g.isNull(0) ? "" : g.getString(0);
                String a = g.isNull(1) ? "" : g.getString(1);
                int d = g.getInt(2);
                int c = g.getInt(3);

                JSONArray items = new JSONArray();
                Cursor m = db.query(
                        DbContract.T_MEDIA_INDEX,
                        new String[]{"id", "file_path", "title", "artist", "album", "profile_id"},
                        "LOWER(TRIM(IFNULL(title,'')))=? AND LOWER(TRIM(IFNULL(artist,'')))=? AND duration_ms=?",
                        new String[]{t, a, String.valueOf(d)},
                        null,
                        null,
                        "added_ts DESC"
                );
                try {
                    while (m.moveToNext()) {
                        JSONObject row = new JSONObject();
                        row.put("id", m.getLong(0));
                        row.put("path", nullToEmpty(m.getString(1)));
                        row.put("title", nullToEmpty(m.getString(2)));
                        row.put("artist", nullToEmpty(m.getString(3)));
                        row.put("album", nullToEmpty(m.getString(4)));
                        row.put("profile_id", m.getLong(5));
                        items.put(row);
                    }
                } finally {
                    m.close();
                }
                if (items.length() > 1) {
                    JSONObject group = new JSONObject();
                    JSONObject head = items.getJSONObject(0);
                    group.put("key_title", head.optString("title", t));
                    group.put("key_artist", head.optString("artist", a));
                    group.put("duration_ms", d);
                    group.put("count", c);
                    group.put("items", items);
                    out.put(group);
                }
            }
        } finally {
            g.close();
        }
        return out;
    }

    private static String mapSortColumn(String sortKey) {
        if ("title".equalsIgnoreCase(sortKey)) {
            return "m.title";
        }
        if ("artist".equalsIgnoreCase(sortKey)) {
            return "m.artist";
        }
        if ("album".equalsIgnoreCase(sortKey)) {
            return "m.album";
        }
        if ("path".equalsIgnoreCase(sortKey)) {
            return "m.file_path";
        }
        return "m.added_ts";
    }

    private static String safeStr(String s, String def) {
        return s == null || s.trim().length() == 0 ? def : s.trim();
    }

    /**
     * Deletes index row, playlist references, and the file on disk.
     *
     * @return error message or null on success
     */
    public String deleteById(long mediaId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor c = db.query(DbContract.T_MEDIA_INDEX, new String[]{"file_path"}, "id=?", new String[]{String.valueOf(mediaId)}, null, null, null, "1");
        String path = null;
        try {
            if (!c.moveToFirst()) {
                return "not found";
            }
            path = c.getString(0);
        } finally {
            c.close();
        }
        db.delete(DbContract.T_PLAYLIST_ENTRIES, "media_file_path=?", new String[]{path});
        db.delete(DbContract.T_MEDIA_INDEX, "id=?", new String[]{String.valueOf(mediaId)});
        File f = new File(path);
        if (f.exists() && f.isFile()) {
            if (!f.delete()) {
                return "file delete failed";
            }
        }
        return null;
    }

    public String artworkDataUrlByMediaId(long mediaId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DbContract.T_MEDIA_INDEX, new String[]{"file_path"}, "id=?",
                new String[]{String.valueOf(mediaId)}, null, null, null, "1");
        try {
            if (!c.moveToFirst()) {
                return "";
            }
            String path = c.getString(0);
            if (path == null || path.length() == 0) {
                return "";
            }
            File file = new File(path);
            if (!file.isFile()) {
                return "";
            }
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(path);
                byte[] art = r.getEmbeddedPicture();
                if (art == null || art.length == 0) {
                    return "";
                }
                if (art.length > 1024 * 1024) {
                    return "";
                }
                return "data:image/jpeg;base64," + Base64.encodeToString(art, Base64.NO_WRAP);
            } catch (Throwable ignored) {
                return "";
            } finally {
                try {
                    r.release();
                } catch (Throwable ignored2) {
                }
            }
        } finally {
            c.close();
        }
    }

    /**
     * Re-reads tags for every row in {@code media_index}.
     */
    public int reindexAllMetadata() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor c = db.query(DbContract.T_MEDIA_INDEX, new String[]{"id", "file_path", "profile_id"}, null, null, null, null, null);
        int n = 0;
        try {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String p = c.getString(1);
                long profileId = c.getLong(2);
                File file = new File(p);
                if (!file.isFile()) {
                    continue;
                }
                ContentValues cv = buildContentValues(profileId, file);
                cv.remove("added_ts");
                db.update(DbContract.T_MEDIA_INDEX, cv, "id=?", new String[]{String.valueOf(id)});
                n++;
            }
        } finally {
            c.close();
        }
        return n;
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
        String name = f.getName().toLowerCase(Locale.US);
        return name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".m4a") || name.endsWith(".ogg")
                || name.endsWith(".wav") || name.endsWith(".aac") || name.endsWith(".opus");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * Deletes orphan {@code .part} files under profile sync roots.
     */
    public int cleanPartFiles(ProfileRepository profiles, StorageBrowser storage) throws JSONException, IOException {
        int deleted = 0;
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
            String ext = p.tempExtension == null || p.tempExtension.length() == 0 ? ".part" : p.tempExtension;
            try {
                File root = storage.resolveSyncDestinationDirectory(p.localRootType, p.localDestination);
                deleted += deleteMatchingSuffix(root, ext);
            } catch (IOException ignored) {
            }
        }
        return deleted;
    }

    private int deleteMatchingSuffix(File dir, String suffix) {
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
                n += deleteMatchingSuffix(f, suffix);
            } else if (f.isFile() && f.getName().endsWith(suffix)) {
                if (f.delete()) {
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * Removes empty directories under sync roots (not the root itself).
     */
    public int pruneEmptyFolders(ProfileRepository profiles, StorageBrowser storage) throws JSONException, IOException {
        int removed = 0;
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
                removed += pruneEmptyUnder(root, root);
            } catch (IOException ignored) {
            }
        }
        return removed;
    }

    private int pruneEmptyUnder(File root, File dir) {
        if (dir == null || !dir.isDirectory()) {
            return 0;
        }
        int n = 0;
        File[] kids = dir.listFiles();
        if (kids != null) {
            for (File f : kids) {
                if (f.isDirectory()) {
                    n += pruneEmptyUnder(root, f);
                }
            }
        }
        kids = dir.listFiles();
        if (kids != null && kids.length == 0 && !dir.equals(root)) {
            if (dir.delete()) {
                n++;
            }
        }
        return n;
    }
}
