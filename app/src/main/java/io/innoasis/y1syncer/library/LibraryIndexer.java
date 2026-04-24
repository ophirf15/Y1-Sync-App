package io.innoasis.y1syncer.library;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;

import io.innoasis.y1syncer.db.DbContract;
import io.innoasis.y1syncer.db.Y1DatabaseHelper;

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
}
