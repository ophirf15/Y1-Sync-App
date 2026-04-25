package io.innoasis.y1syncer.library;

import android.media.MediaMetadataRetriever;

import java.io.File;

/**
 * Reads embedded tags using the platform API (no extra native deps).
 */
public final class AudioMetadataReader {

    private AudioMetadataReader() {
    }

    public static class Tags {
        public String artist = "";
        public String album = "";
        public String title = "";
        public int durationMs;
        public int trackNo;
        public int year;
    }

    public static Tags read(File file) {
        Tags t = new Tags();
        if (file == null || !file.isFile()) {
            return t;
        }
        String fallbackTitle = file.getName();
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(file.getAbsolutePath());
            t.artist = firstNonEmpty(
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST));
            t.album = nz(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
            t.title = firstNonEmpty(
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    fallbackTitle);
            t.durationMs = parseIntSafe(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION), 0);
            t.trackNo = parseIntSafe(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER), 0);
            t.year = parseYear(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR));
        } catch (Throwable ignored) {
            t.title = fallbackTitle;
        } finally {
            try {
                r.release();
            } catch (Throwable ignored2) {
            }
        }
        if (t.title == null || t.title.length() == 0) {
            t.title = fallbackTitle;
        }
        return t;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static String firstNonEmpty(String... vals) {
        if (vals == null) {
            return "";
        }
        for (String v : vals) {
            if (v != null && v.trim().length() > 0) {
                return v.trim();
            }
        }
        return "";
    }

    private static int parseIntSafe(String s, int def) {
        if (s == null || s.trim().length() == 0) {
            return def;
        }
        try {
            String t = s.trim();
            int slash = t.indexOf('/');
            if (slash > 0) {
                t = t.substring(0, slash);
            }
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int parseYear(String s) {
        if (s == null || s.trim().length() == 0) {
            return 0;
        }
        String t = s.trim();
        if (t.length() >= 4) {
            try {
                return Integer.parseInt(t.substring(0, 4));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
