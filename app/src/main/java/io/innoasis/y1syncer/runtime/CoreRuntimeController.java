package io.innoasis.y1syncer.runtime;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

import io.innoasis.y1syncer.BuildConfig;
import io.innoasis.y1syncer.db.Y1DatabaseHelper;
import io.innoasis.y1syncer.db.repos.LogRepository;
import io.innoasis.y1syncer.db.repos.PlaylistRepository;
import io.innoasis.y1syncer.db.repos.ProfileRepository;
import io.innoasis.y1syncer.db.repos.UpdateBundleRepository;
import io.innoasis.y1syncer.library.LibraryIndexer;
import io.innoasis.y1syncer.scheduler.SyncAlarmScheduler;
import io.innoasis.y1syncer.server.ApiRouter;
import io.innoasis.y1syncer.server.AssetResolver;
import io.innoasis.y1syncer.server.EmbeddedHttpServer;
import io.innoasis.y1syncer.smb.SmbBrowser;
import io.innoasis.y1syncer.smb.SmbConnectionProbe;
import io.innoasis.y1syncer.storage.StorageBrowser;
import io.innoasis.y1syncer.sync.SyncOrchestrator;
import io.innoasis.y1syncer.updates.BundleStorage;
import io.innoasis.y1syncer.updates.GitHubReleaseChecker;
import io.innoasis.y1syncer.updates.WebBundleUpdateManager;
import io.innoasis.y1syncer.util.BatteryUtil;
import io.innoasis.y1syncer.util.NetUtil;
import io.innoasis.y1syncer.util.StorageUtil;

public class CoreRuntimeController {
    public static final String DEFAULT_MANIFEST_URL =
            "https://raw.githubusercontent.com/ophirf15/Y1-Sync-App/main/docs/web-bundle/manifest.json";

    private static final int DEFAULT_PORT = 8081;
    private static final String PREFS_NAME = "runtime_prefs";
    private static final String KEY_SERVER_PORT = "server_port";
    private static final String KEY_MANIFEST_URL = "manifest_url";
    private static final String KEY_AUTO_SYNC = "auto_sync";
    private static final String KEY_LAST_LIB_SCAN_TS = "last_lib_scan_ts";
    private static final String KEY_LAST_LIB_SCAN_COUNT = "last_lib_scan_count";
    private static final String KEY_LAST_BUNDLE_CHECK_TS = "last_bundle_check_ts";
    private static final String KEY_LAST_BUNDLE_CHECK_JSON = "last_bundle_check_json";
    private static final String KEY_LAST_RELEASE_TS = "last_release_check_ts";
    private static final String KEY_LAST_RELEASE_JSON = "last_release_json";

    private final Context appContext;
    private final SharedPreferences prefs;
    private final Y1DatabaseHelper dbHelper;
    private final ProfileRepository profileRepository;
    private final LogRepository logRepository;
    private final UpdateBundleRepository updateBundleRepository;
    private final PlaylistRepository playlistRepository;
    private final BundleStorage bundleStorage;
    private final WebBundleUpdateManager webBundleUpdateManager;
    private final SyncOrchestrator syncOrchestrator;
    private final SyncAlarmScheduler syncAlarmScheduler;
    private final StorageBrowser storageBrowser;
    private final LibraryIndexer libraryIndexer;

    private EmbeddedHttpServer server;
    private int serverPort;
    private boolean autoSyncEnabled;
    private String lastSyncStatus = "Never synced";
    private String manifestUrl;

    public CoreRuntimeController(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.dbHelper = new Y1DatabaseHelper(appContext);
        this.profileRepository = new ProfileRepository(dbHelper);
        this.logRepository = new LogRepository(dbHelper);
        this.updateBundleRepository = new UpdateBundleRepository(dbHelper);
        this.playlistRepository = new PlaylistRepository(dbHelper);
        this.bundleStorage = new BundleStorage(appContext, updateBundleRepository);
        this.webBundleUpdateManager = new WebBundleUpdateManager(bundleStorage, updateBundleRepository, logRepository);
        this.storageBrowser = new StorageBrowser(appContext);
        this.libraryIndexer = new LibraryIndexer(dbHelper);
        this.syncOrchestrator = new SyncOrchestrator(appContext, logRepository, profileRepository, storageBrowser, libraryIndexer);
        this.syncAlarmScheduler = new SyncAlarmScheduler(appContext);
        this.serverPort = loadServerPort();
        this.manifestUrl = prefs.getString(KEY_MANIFEST_URL, DEFAULT_MANIFEST_URL);
        this.autoSyncEnabled = prefs.getBoolean(KEY_AUTO_SYNC, false);
        if (autoSyncEnabled) {
            syncAlarmScheduler.scheduleInterval(6 * 60 * 60 * 1000L);
        }
        this.profileRepository.ensureDefaultProfile();
    }

    public void startServer() throws Exception {
        if (server != null) {
            return;
        }
        server = new EmbeddedHttpServer(serverPort, new ApiRouter(this), new AssetResolver(appContext, bundleStorage));
        server.start();
        logRepository.addLog("INFO", "Server started on port " + serverPort);
    }

    public void stopServer() {
        if (server == null) {
            return;
        }
        server.stop();
        server = null;
        logRepository.addLog("INFO", "Server stopped");
    }

    public void syncNow(String trigger) {
        String summary = syncOrchestrator.syncNow(null);
        lastSyncStatus = "[" + trigger + "] " + summary;
    }

    public JSONObject checkForBundleUpdates() throws JSONException {
        JSONObject json = webBundleUpdateManager.checkForUpdate(manifestUrl);
        prefs.edit()
                .putLong(KEY_LAST_BUNDLE_CHECK_TS, System.currentTimeMillis())
                .putString(KEY_LAST_BUNDLE_CHECK_JSON, json.toString())
                .apply();
        return json;
    }

    public JSONObject downloadAndApplyBundleUpdate() throws JSONException {
        JSONObject json = webBundleUpdateManager.downloadAndApply(manifestUrl);
        prefs.edit()
                .putLong(KEY_LAST_BUNDLE_CHECK_TS, System.currentTimeMillis())
                .putString(KEY_LAST_BUNDLE_CHECK_JSON, json.toString())
                .apply();
        return json;
    }

    public JSONObject restartServerForUpdates() throws JSONException {
        try {
            boolean running = server != null;
            if (running) {
                stopServer();
                startServer();
            }
            return new JSONObject().put("restarted", running);
        } catch (Exception e) {
            return new JSONObject().put("restarted", false).put("error", e.getMessage());
        }
    }

    public void revertBundledUi() {
        webBundleUpdateManager.revertToBundled();
    }

    public void setAutoSyncEnabled(boolean enabled) {
        autoSyncEnabled = enabled;
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply();
        if (enabled) {
            syncAlarmScheduler.scheduleInterval(6 * 60 * 60 * 1000L);
        } else {
            syncAlarmScheduler.cancel();
        }
    }

    public boolean isAutoSyncEnabled() {
        return autoSyncEnabled;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int newPort) throws Exception {
        if (newPort < 1 || newPort > 65535) {
            throw new IllegalArgumentException("Port must be 1-65535");
        }
        boolean restartNeeded = server != null;
        if (restartNeeded) {
            stopServer();
        }
        serverPort = newPort;
        saveServerPort(newPort);
        logRepository.addLog("INFO", "Server port changed to " + newPort);
        if (restartNeeded) {
            startServer();
        }
    }

    private int loadServerPort() {
        return prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT);
    }

    private void saveServerPort(int port) {
        prefs.edit().putInt(KEY_SERVER_PORT, port).apply();
    }

    public RuntimeStatusSnapshot getStatusSnapshot() {
        RuntimeStatusSnapshot s = new RuntimeStatusSnapshot();
        s.serverRunning = server != null;
        s.serverPort = serverPort;
        s.localIp = NetUtil.getWifiIp(appContext);
        s.currentProfile = "Music";
        s.lastSyncStatus = lastSyncStatus;
        s.autoSyncEnabled = autoSyncEnabled;
        s.storageSummary = StorageUtil.getStorageSummary();
        s.batterySummary = BatteryUtil.getBatterySummary(appContext);
        s.updateStatus = "Bundled fallback ready";
        return s;
    }

    public JSONObject getStatusJson() throws JSONException {
        RuntimeStatusSnapshot s = getStatusSnapshot();
        JSONObject json = new JSONObject();
        json.put("server_running", s.serverRunning);
        json.put("ip", s.localIp);
        json.put("port", s.serverPort);
        json.put("profile", s.currentProfile);
        json.put("last_sync", s.lastSyncStatus);
        json.put("auto_sync", s.autoSyncEnabled);
        json.put("storage", s.storageSummary);
        json.put("battery", s.batterySummary);
        return json;
    }

    public JSONObject getDeviceInfoJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("model", android.os.Build.MODEL);
        json.put("android", android.os.Build.VERSION.RELEASE);
        json.put("sdk", android.os.Build.VERSION.SDK_INT);
        json.put("port", serverPort);
        return json;
    }

    public JSONArray getLogsJson() throws JSONException {
        return logRepository.getLogsJson();
    }

    public JSONArray getProfilesJson() throws JSONException {
        return profileRepository.listProfiles();
    }

    public JSONObject createProfile(JSONObject payload) throws JSONException {
        long id = profileRepository.createProfile(payload);
        logRepository.addLog("INFO", "Created profile id=" + id);
        return new JSONObject().put("created", id > 0).put("id", id);
    }

    public JSONObject getProfileJson(long id) throws JSONException {
        JSONObject profile = profileRepository.getProfile(id);
        if (profile == null) {
            return new JSONObject().put("error", "Profile not found");
        }
        return profile;
    }

    public JSONObject updateProfile(long id, JSONObject payload) throws JSONException {
        return new JSONObject().put("updated", profileRepository.updateProfile(id, payload));
    }

    public JSONObject deleteProfile(long id) throws JSONException {
        return new JSONObject().put("deleted", profileRepository.deleteProfile(id));
    }

    public JSONObject duplicateProfile(long id) throws JSONException {
        long newId = profileRepository.duplicateProfile(id);
        return new JSONObject().put("duplicated", newId > 0).put("id", newId);
    }

    public JSONObject setProfileActive(long id, boolean active) throws JSONException {
        return new JSONObject().put("updated", profileRepository.setProfileActive(id, active)).put("is_active", active);
    }

    public JSONObject testProfileConnection(long id, JSONObject formOverride) throws JSONException {
        JSONObject profile = profileRepository.getProfile(id);
        if (profile == null) {
            return new JSONObject().put("ok", false).put("message", "Profile not found");
        }
        JSONObject merged = new JSONObject(profile.toString());
        mergeProfileOverrides(merged, formOverride);
        if (merged.optString("host", "").trim().isEmpty()) {
            return new JSONObject()
                    .put("ok", false)
                    .put("message", "Enter a Host (IP or hostname) in the Host field (row under Profile Name).");
        }
        if (merged.optString("password", "").isEmpty()) {
            String stored = profileRepository.getPasswordEnc(id);
            if (stored != null && stored.length() > 0) {
                merged.put("password", stored);
            }
        }
        if (!"SMB".equalsIgnoreCase(merged.optString("protocol", "SMB"))) {
            return new JSONObject()
                    .put("ok", true)
                    .put("message", "Host is set. Live SMB connection test runs only when Protocol is SMB.");
        }
        return SmbConnectionProbe.probe(merged);
    }

    private static void mergeProfileOverrides(JSONObject base, JSONObject over) throws JSONException {
        if (base == null || over == null || over.length() == 0) {
            return;
        }
        JSONArray names = over.names();
        if (names == null) {
            return;
        }
        for (int i = 0; i < names.length(); i++) {
            String key = names.getString(i);
            if (!over.isNull(key)) {
                base.put(key, over.get(key));
            }
        }
    }

    public JSONObject syncProfileNow(long id) throws JSONException {
        JSONObject profile = profileRepository.getProfile(id);
        if (profile == null) {
            return new JSONObject().put("accepted", false).put("error", "Profile not found");
        }
        String summary = syncOrchestrator.syncProfileById(id);
        lastSyncStatus = "Profile " + id + ": " + summary;
        return new JSONObject().put("accepted", true);
    }

    public JSONObject getSyncStatusJson() throws JSONException {
        return new JSONObject()
                .put("running", false)
                .put("last_sync", lastSyncStatus)
                .put("auto_sync", autoSyncEnabled);
    }

    public JSONArray getSyncRunsJson() throws JSONException {
        JSONArray runs = new JSONArray();
        runs.put(new JSONObject().put("profile", "Music").put("trigger", "manual").put("result", "ok"));
        return runs;
    }

    public JSONObject getLibraryScanStatusJson() throws JSONException {
        long ts = prefs.getLong(KEY_LAST_LIB_SCAN_TS, 0);
        int cnt = prefs.getInt(KEY_LAST_LIB_SCAN_COUNT, 0);
        return new JSONObject()
                .put("state", "idle")
                .put("last_scan_ts", ts)
                .put("last_scan_count", cnt);
    }

    public JSONArray getLibraryItemsJson() throws JSONException {
        return getLibraryItemsJson(null);
    }

    public JSONArray getLibraryItemsJson(Map<String, String> queryParams) throws JSONException {
        return libraryIndexer.queryItemsJson(queryParams);
    }

    public JSONObject deleteLibraryItem(long mediaId) throws JSONException {
        String err = libraryIndexer.deleteById(mediaId);
        if (err != null) {
            return new JSONObject().put("ok", false).put("error", err);
        }
        logRepository.addLog("INFO", "Deleted library item id=" + mediaId);
        return new JSONObject().put("ok", true);
    }

    public JSONObject reindexLibraryMetadata() throws JSONException {
        int n = libraryIndexer.reindexAllMetadata();
        logRepository.addLog("INFO", "Reindexed metadata for " + n + " tracks");
        return new JSONObject().put("ok", true).put("reindexed", n);
    }

    public JSONArray getPlaylistsJson() throws JSONException {
        return playlistRepository.listSummaries();
    }

    public JSONObject getPlaylistJson(long id) throws JSONException {
        JSONObject p = playlistRepository.get(id);
        if (p == null) {
            return new JSONObject().put("error", "not found");
        }
        return p;
    }

    public JSONObject createPlaylist(JSONObject body) throws JSONException {
        String name = body == null ? "" : body.optString("name", "");
        long id = playlistRepository.create(name);
        return new JSONObject().put("ok", id > 0).put("id", id);
    }

    public JSONObject updatePlaylist(long id, JSONObject body) throws JSONException {
        String name = body == null ? "" : body.optString("name", "");
        boolean ok = playlistRepository.rename(id, name);
        return new JSONObject().put("ok", ok);
    }

    public JSONObject deletePlaylist(long id) throws JSONException {
        boolean ok = playlistRepository.delete(id);
        return new JSONObject().put("ok", ok);
    }

    public JSONArray getPlaylistEntriesJson(long playlistId) throws JSONException {
        return playlistRepository.listEntries(playlistId);
    }

    public JSONObject addPlaylistTracks(long playlistId, JSONObject body) throws JSONException {
        JSONArray paths = body == null ? null : body.optJSONArray("paths");
        int added = 0;
        if (paths != null && paths.length() > 0) {
            for (int i = 0; i < paths.length(); i++) {
                String p = paths.optString(i, "");
                if (playlistRepository.addTrack(playlistId, p) > 0) {
                    added++;
                }
            }
        } else {
            String single = body == null ? "" : body.optString("path", "");
            if (single.length() > 0 && playlistRepository.addTrack(playlistId, single) > 0) {
                added++;
            }
        }
        return new JSONObject().put("ok", true).put("added", added);
    }

    public JSONObject removePlaylistEntry(long entryId) throws JSONException {
        return new JSONObject().put("ok", playlistRepository.removeEntry(entryId));
    }

    public JSONObject reorderPlaylistEntries(long playlistId, JSONObject body) throws JSONException {
        JSONArray order = body == null ? null : body.optJSONArray("order");
        boolean ok = order != null && playlistRepository.setOrder(playlistId, order);
        return new JSONObject().put("ok", ok);
    }

    public JSONObject duplicatePlaylist(long id) throws JSONException {
        long newId = playlistRepository.duplicate(id);
        return new JSONObject().put("ok", newId > 0).put("id", newId);
    }

    public String exportPlaylistM3u8(long playlistId) throws JSONException {
        return playlistRepository.buildM3u8Body(playlistId);
    }

    public JSONObject getSettingsJson() throws JSONException {
        return new JSONObject()
                .put("server_port", serverPort)
                .put("manifest_url", manifestUrl)
                .put("auto_sync", autoSyncEnabled);
    }

    public JSONObject setManifestUrlSetting(JSONObject body) throws JSONException {
        String url = body == null ? "" : body.optString("url", "").trim();
        if (url.length() == 0) {
            return new JSONObject().put("ok", false).put("error", "url required");
        }
        manifestUrl = url;
        prefs.edit().putString(KEY_MANIFEST_URL, url).apply();
        logRepository.addLog("INFO", "Manifest URL updated");
        return new JSONObject().put("ok", true).put("manifest_url", manifestUrl);
    }

    public JSONObject setServerPortSetting(JSONObject body) throws JSONException {
        int port = body == null ? 0 : body.optInt("port", 0);
        if (port < 1) {
            return new JSONObject().put("ok", false).put("error", "port required");
        }
        try {
            setServerPort(port);
            return new JSONObject().put("ok", true).put("port", serverPort);
        } catch (Exception e) {
            return new JSONObject().put("ok", false).put("error", e.getMessage());
        }
    }

    public JSONObject setAutoSyncSetting(JSONObject body) throws JSONException {
        boolean en = body != null && body.optBoolean("enabled", false);
        setAutoSyncEnabled(en);
        return new JSONObject().put("ok", true).put("auto_sync", autoSyncEnabled);
    }

    public JSONObject getUpdatesStatusJson() throws JSONException {
        JSONObject active = updateBundleRepository.getActiveBundle();
        String activeVersion = active == null ? "bundled" : active.optString("resource_version", "bundled");
        long lastTs = prefs.getLong(KEY_LAST_BUNDLE_CHECK_TS, 0);
        String lastBundle = prefs.getString(KEY_LAST_BUNDLE_CHECK_JSON, "");
        JSONObject out = new JSONObject();
        out.put("app_version", BuildConfig.VERSION_NAME);
        out.put("active_bundle_version", activeVersion);
        out.put("fallback_bundle_version", "bundled");
        out.put("manifest_url", manifestUrl);
        out.put("last_bundle_check_ts", lastTs);
        if (lastBundle.length() > 0) {
            try {
                out.put("last_bundle_check", new JSONObject(lastBundle));
            } catch (JSONException e) {
                out.put("last_bundle_check", lastBundle);
            }
        } else {
            out.put("last_bundle_check", JSONObject.NULL);
        }
        JSONObject rel = fetchOrCachedRelease();
        if (rel != null && !rel.has("error")) {
            out.put("github_release", rel);
            String tag = rel.optString("tag_name", "");
            out.put("apk_update_available", GitHubReleaseChecker.isNewerThanCurrent(tag, BuildConfig.VERSION_NAME));
            out.put("latest_release_url", rel.optString("html_url", ""));
            out.put("apk_download_url", rel.optString("apk_download_url", ""));
        } else {
            out.put("github_release", rel == null ? JSONObject.NULL : rel);
            out.put("apk_update_available", false);
            out.put("latest_release_url", "");
            out.put("apk_download_url", "");
        }
        return out;
    }

    private JSONObject fetchOrCachedRelease() throws JSONException {
        long now = System.currentTimeMillis();
        long ts = prefs.getLong(KEY_LAST_RELEASE_TS, 0);
        String cached = prefs.getString(KEY_LAST_RELEASE_JSON, "");
        if (cached.length() > 0 && now - ts < 10 * 60 * 1000L) {
            JSONObject j = new JSONObject(cached);
            if (!j.has("error")) {
                return j;
            }
        }
        JSONObject rel = GitHubReleaseChecker.fetchLatestRelease();
        prefs.edit().putLong(KEY_LAST_RELEASE_TS, now).putString(KEY_LAST_RELEASE_JSON, rel.toString()).apply();
        return rel;
    }

    public JSONArray getFailedDownloadsJson() throws JSONException {
        return logRepository.searchMessages("Download failed", 50);
    }

    public JSONArray getIncompleteDownloadsJson() throws JSONException {
        return logRepository.searchMessages(".part", 50);
    }

    public JSONObject maintenanceAction(String action) throws JSONException {
        logRepository.addLog("INFO", "Maintenance action: " + action);
        if ("rescan-library".equals(action) || "rebuild-sync-index".equals(action)) {
            try {
                int n = libraryIndexer.rescanFromProfiles(appContext, profileRepository, storageBrowser);
                prefs.edit().putLong(KEY_LAST_LIB_SCAN_TS, System.currentTimeMillis()).putInt(KEY_LAST_LIB_SCAN_COUNT, n).apply();
                logRepository.addLog("INFO", "Library rescan indexed " + n + " audio files");
                return new JSONObject().put("ok", true).put("action", action).put("indexed", n);
            } catch (Exception e) {
                logRepository.addLog("ERROR", "Library rescan failed: " + e.getMessage());
                return new JSONObject().put("ok", false).put("action", action).put("error", e.getMessage());
            }
        }
        if ("clean-part-files".equals(action)) {
            try {
                int n = libraryIndexer.cleanPartFiles(profileRepository, storageBrowser);
                logRepository.addLog("INFO", "Cleaned " + n + " .part files");
                return new JSONObject().put("ok", true).put("action", action).put("deleted", n);
            } catch (Exception e) {
                return new JSONObject().put("ok", false).put("action", action).put("error", e.getMessage());
            }
        }
        if ("prune-empty-folders".equals(action)) {
            try {
                int n = libraryIndexer.pruneEmptyFolders(profileRepository, storageBrowser);
                logRepository.addLog("INFO", "Pruned " + n + " empty folders");
                return new JSONObject().put("ok", true).put("action", action).put("pruned", n);
            } catch (Exception e) {
                return new JSONObject().put("ok", false).put("action", action).put("error", e.getMessage());
            }
        }
        return new JSONObject().put("ok", true).put("action", action);
    }

    public JSONArray getStorageRootsJson() throws JSONException {
        return storageBrowser.listRoots();
    }

    public JSONArray getStorageChildrenJson(Map<String, String> queryParams) throws JSONException {
        return storageBrowser.listChildren(queryParams);
    }

    public JSONObject smbBrowse(JSONObject body) throws JSONException {
        return SmbBrowser.browse(body);
    }
}
