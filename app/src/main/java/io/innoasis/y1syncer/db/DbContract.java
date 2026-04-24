package io.innoasis.y1syncer.db;

public final class DbContract {
    private DbContract() {
    }

    public static final String DB_NAME = "y1runtime.db";
    public static final int DB_VERSION = 1;

    public static final String T_PROFILES = "profiles";
    public static final String T_SYNC_STATE = "sync_state";
    public static final String T_SYNC_RUNS = "sync_runs";
    public static final String T_MEDIA_INDEX = "media_index";
    public static final String T_PLAYLISTS = "playlists";
    public static final String T_PLAYLIST_ENTRIES = "playlist_entries";
    public static final String T_UPDATE_BUNDLES = "update_bundles";
    public static final String T_APP_LOGS = "app_logs";
}
