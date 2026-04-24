package io.innoasis.y1syncer.util;

import android.os.Environment;
import android.os.StatFs;

import java.io.File;

public final class StorageUtil {
    private StorageUtil() {
    }

    public static String getStorageSummary() {
        File root = Environment.getExternalStorageDirectory();
        if (root == null) {
            return "Storage unavailable";
        }
        StatFs statFs = new StatFs(root.getAbsolutePath());
        long total = (long) statFs.getBlockCount() * (long) statFs.getBlockSize();
        long free = (long) statFs.getAvailableBlocks() * (long) statFs.getBlockSize();
        return "Storage free " + (free / (1024 * 1024)) + "MB / " + (total / (1024 * 1024)) + "MB";
    }
}
