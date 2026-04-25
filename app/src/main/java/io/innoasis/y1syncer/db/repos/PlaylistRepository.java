package io.innoasis.y1syncer.db.repos;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.innoasis.y1syncer.db.DbContract;
import io.innoasis.y1syncer.db.Y1DatabaseHelper;

public class PlaylistRepository {
    private final Y1DatabaseHelper dbHelper;

    public PlaylistRepository(Y1DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public JSONArray listSummaries() throws JSONException {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT p.id, p.name, "
                + "(SELECT COUNT(*) FROM " + DbContract.T_PLAYLIST_ENTRIES + " e WHERE e.playlist_id = p.id) AS cnt "
                + "FROM " + DbContract.T_PLAYLISTS + " p ORDER BY p.updated_at DESC";
        Cursor c = db.rawQuery(sql, null);
        try {
            JSONArray arr = new JSONArray();
            while (c.moveToNext()) {
                JSONObject row = new JSONObject();
                row.put("id", c.getLong(0));
                row.put("name", c.getString(1));
                row.put("entries", c.getInt(2));
                arr.put(row);
            }
            return arr;
        } finally {
            c.close();
        }
    }

    public long create(String name) throws JSONException {
        if (name == null || name.trim().length() == 0) {
            return 0;
        }
        String n = name.trim();
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues cv = new ContentValues();
        cv.put("name", n);
        cv.put("created_at", now);
        cv.put("updated_at", now);
        long id = db.insert(DbContract.T_PLAYLISTS, null, cv);
        if (id < 0) {
            cv.put("name", n + " " + now);
            id = db.insert(DbContract.T_PLAYLISTS, null, cv);
        }
        return id;
    }

    public boolean rename(long id, String name) {
        if (name == null || name.trim().length() == 0) {
            return false;
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name.trim());
        cv.put("updated_at", System.currentTimeMillis());
        try {
            return db.update(DbContract.T_PLAYLISTS, cv, "id=?", new String[]{String.valueOf(id)}) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(DbContract.T_PLAYLIST_ENTRIES, "playlist_id=?", new String[]{String.valueOf(id)});
        return db.delete(DbContract.T_PLAYLISTS, "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public JSONObject get(long id) throws JSONException {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DbContract.T_PLAYLISTS, null, "id=?", new String[]{String.valueOf(id)}, null, null, null, "1");
        try {
            if (!c.moveToFirst()) {
                return null;
            }
            JSONObject o = new JSONObject();
            o.put("id", c.getLong(c.getColumnIndex("id")));
            o.put("name", c.getString(c.getColumnIndex("name")));
            o.put("encoding", c.getString(c.getColumnIndex("encoding")));
            o.put("relative_paths", c.getInt(c.getColumnIndex("relative_paths")));
            return o;
        } finally {
            c.close();
        }
    }

    public JSONArray listEntries(long playlistId) throws JSONException {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT e.id, e.position, e.media_file_path, "
                + "IFNULL(m.artist,''), IFNULL(m.album,''), IFNULL(m.title,'') "
                + "FROM " + DbContract.T_PLAYLIST_ENTRIES + " e "
                + "LEFT JOIN " + DbContract.T_MEDIA_INDEX + " m ON m.file_path = e.media_file_path "
                + "WHERE e.playlist_id=? ORDER BY e.position ASC, e.id ASC";
        Cursor c = db.rawQuery(sql, new String[]{String.valueOf(playlistId)});
        try {
            JSONArray arr = new JSONArray();
            while (c.moveToNext()) {
                JSONObject row = new JSONObject();
                row.put("entry_id", c.getLong(0));
                row.put("position", c.getInt(1));
                row.put("path", c.getString(2));
                row.put("artist", c.getString(3));
                row.put("album", c.getString(4));
                row.put("title", c.getString(5));
                arr.put(row);
            }
            return arr;
        } finally {
            c.close();
        }
    }

    public long addTrack(long playlistId, String absolutePath) {
        if (absolutePath == null || absolutePath.trim().length() == 0) {
            return 0;
        }
        String path = absolutePath.trim();
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor dup = db.query(DbContract.T_PLAYLIST_ENTRIES, new String[]{"id"}, "playlist_id=? AND media_file_path=?",
                new String[]{String.valueOf(playlistId), path}, null, null, null, "1");
        try {
            if (dup.moveToFirst()) {
                return dup.getLong(0);
            }
        } finally {
            dup.close();
        }
        int nextPos = 0;
        Cursor mx = db.rawQuery("SELECT MAX(position) FROM " + DbContract.T_PLAYLIST_ENTRIES + " WHERE playlist_id=?",
                new String[]{String.valueOf(playlistId)});
        try {
            if (mx.moveToFirst() && !mx.isNull(0)) {
                nextPos = mx.getInt(0) + 1;
            }
        } finally {
            mx.close();
        }
        ContentValues cv = new ContentValues();
        cv.put("playlist_id", playlistId);
        cv.put("position", nextPos);
        cv.put("media_file_path", path);
        long id = db.insert(DbContract.T_PLAYLIST_ENTRIES, null, cv);
        touchPlaylist(db, playlistId);
        return id;
    }

    public boolean removeEntry(long entryId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor c = db.query(DbContract.T_PLAYLIST_ENTRIES, new String[]{"playlist_id"}, "id=?", new String[]{String.valueOf(entryId)}, null, null, null, "1");
        long pl = 0;
        try {
            if (!c.moveToFirst()) {
                return false;
            }
            pl = c.getLong(0);
        } finally {
            c.close();
        }
        int n = db.delete(DbContract.T_PLAYLIST_ENTRIES, "id=?", new String[]{String.valueOf(entryId)});
        if (n > 0) {
            renumberPositions(db, pl);
            touchPlaylist(db, pl);
        }
        return n > 0;
    }

    public boolean setOrder(long playlistId, JSONArray orderEntryIds) throws JSONException {
        if (orderEntryIds == null) {
            return false;
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < orderEntryIds.length(); i++) {
                long eid = orderEntryIds.getLong(i);
                ContentValues cv = new ContentValues();
                cv.put("position", i);
                db.update(DbContract.T_PLAYLIST_ENTRIES, cv, "id=? AND playlist_id=?",
                        new String[]{String.valueOf(eid), String.valueOf(playlistId)});
            }
            db.setTransactionSuccessful();
            touchPlaylist(db, playlistId);
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private static void renumberPositions(SQLiteDatabase db, long playlistId) {
        Cursor c = db.query(DbContract.T_PLAYLIST_ENTRIES, new String[]{"id"}, "playlist_id=?",
                new String[]{String.valueOf(playlistId)}, null, null, "position ASC, id ASC");
        try {
            int pos = 0;
            while (c.moveToNext()) {
                long id = c.getLong(0);
                ContentValues cv = new ContentValues();
                cv.put("position", pos++);
                db.update(DbContract.T_PLAYLIST_ENTRIES, cv, "id=?", new String[]{String.valueOf(id)});
            }
        } finally {
            c.close();
        }
    }

    private static void touchPlaylist(SQLiteDatabase db, long playlistId) {
        ContentValues cv = new ContentValues();
        cv.put("updated_at", System.currentTimeMillis());
        db.update(DbContract.T_PLAYLISTS, cv, "id=?", new String[]{String.valueOf(playlistId)});
    }

    public long duplicate(long id) throws JSONException {
        JSONObject src = get(id);
        if (src == null) {
            return 0;
        }
        String base = src.optString("name", "Playlist") + " copy";
        long newId = create(base);
        if (newId <= 0) {
            return 0;
        }
        JSONArray entries = listEntries(id);
        for (int i = 0; i < entries.length(); i++) {
            JSONObject e = entries.getJSONObject(i);
            addTrack(newId, e.optString("path", ""));
        }
        return newId;
    }

    /**
     * M3U8 body: one absolute path per line after header.
     */
    public String buildM3u8Body(long playlistId) throws JSONException {
        JSONArray entries = listEntries(playlistId);
        StringBuilder sb = new StringBuilder();
        sb.append("#EXTM3U\n");
        for (int i = 0; i < entries.length(); i++) {
            sb.append(entries.getJSONObject(i).optString("path", "")).append("\n");
        }
        return sb.toString();
    }

    public List<String> entryPaths(long playlistId) throws JSONException {
        JSONArray arr = listEntries(playlistId);
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < arr.length(); i++) {
            out.add(arr.getJSONObject(i).optString("path", ""));
        }
        return out;
    }
}
