package io.innoasis.y1syncer.util;

import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.util.Locale;

/**
 * Notifies the system media database so files appear in Music / gallery apps.
 */
public final class MediaScanHelper {

    private MediaScanHelper() {
    }

    public static void scanFile(Context context, File file) {
        if (context == null || file == null || !file.isFile()) {
            return;
        }
        String path = file.getAbsolutePath();
        String mime = guessMime(file.getName());
        try {
            Context app = context.getApplicationContext();
            if (Build.VERSION.SDK_INT >= 29) {
                String[] paths = new String[]{path};
                String[] mimes = mime != null ? new String[]{mime} : null;
                MediaScannerConnection.scanFile(app, paths, mimes, null);
            } else {
                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                intent.setData(Uri.fromFile(file));
                app.sendBroadcast(intent);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String guessMime(String fileName) {
        if (fileName == null) {
            return null;
        }
        String n = fileName.toLowerCase(Locale.US);
        if (n.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (n.endsWith(".flac")) {
            return "audio/flac";
        }
        if (n.endsWith(".m4a") || n.endsWith(".aac")) {
            return "audio/mp4";
        }
        if (n.endsWith(".ogg")) {
            return "audio/ogg";
        }
        if (n.endsWith(".wav")) {
            return "audio/wav";
        }
        if (n.endsWith(".opus")) {
            return "audio/opus";
        }
        return null;
    }
}
