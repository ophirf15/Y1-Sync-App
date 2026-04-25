package io.innoasis.y1syncer.runtime;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

import io.innoasis.y1syncer.db.Y1DatabaseHelper;
import io.innoasis.y1syncer.db.repos.LogRepository;
import io.innoasis.y1syncer.db.repos.ProfileRepository;
import io.innoasis.y1syncer.db.repos.UpdateBundleRepository;
import io.innoasis.y1syncer.scheduler.SyncAlarmScheduler;
import io.innoasis.y1syncer.server.ApiRouter;
import io.innoasis.y1syncer.server.AssetResolver;
import io.innoasis.y1syncer.server.EmbeddedHttpServer;
import io.innoasis.y1syncer.smb.SmbBrowser;
import io.innoasis.y1syncer.smb.SmbConnectionProbe;
import io.innoasis.y1syncer.library.LibraryIndexer;
import io.innoasis.y1syncer.storage.StorageBrowser;
import io.innoasis.y1syncer.sync.SyncOrchestrator;
import io.innoasis.y1syncer.updates.BundleStorage;
import io.innoasis.y1syncer.updates.WebBundleUpdateManager;
import io.innoasis.y1syncer.util.BatteryUtil;
import io.innoasis.y1syncer.util.NetUtil;
import io.innoasis.y1syncer.util.StorageUtil;

public class CoreRuntimeController {
    private static final int DEFAULT_PORT = 8081;
    private static final String PREFS_NAME = "runtime_prefs";
    private static final String KEY_SERVER_PORT = "server_port";

    private final Context appContext;
    private final Y1DatabaseHelper dbHelper;
    private final ProfileRepository profileRepository;
    private final LogRepository logRepository;
    private final UpdateBundleRepository updateBundleRepository;
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
    private String manifestUrl = "http://192.168.1.10/y1-web/manifest.json";

    public CoreRuntimeController(Context context) {
        this.appContext = context.getApplicationContext();
        this.dbHelper = new Y1DatabaseHelper(appContext);
        this.profileRepository = new ProfileRepository(dbHelper);
        this.logRepository = new LogRepository(dbHelper);
        this.updateBundleRepository = new UpdateBundleRepository(dbHelper);
        this.bundleStorage = new BundleStorage(appContext, updateBundleRepository);
        this.webBundleUpdateManager = new WebBundleUpdateManager(bundleStorage, updateBundleRepository, logRepository);
        this.storageBrowser = new StorageBrowser(appContext);
        this.libraryIndexer = new LibraryIndexer(dbHelper);
        this.syncOrchestrator = new SyncOrchestrator(appContext, logRepository, profileRepository, storageBrowser, libraryIndexer);
        this.syncAlarmScheduler = new SyncAlarmScheduler(appContext);
        this.serverPort = loadServerPort();
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
        return webBundleUpdateManager.checkForUpdate(manifestUrl);
    }

    public JSONObject downloadAndApplyBundleUpdate() throws JSONException {
        return webBundleUpdateManager.downloadAndApply(manifestUrl);
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
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT);
    }

    private void saveServerPort(int port) {
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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
        return new JSONObject().put("state", "idle").put("last_scan", System.currentTimeMillis());
    }

    public JSONArray getLibraryItemsJson() throws JSONException {
        return libraryIndexer.queryItemsJson();
    }

    public JSONArray getPlaylistsJson() throws JSONException {
        JSONArray arr = new JSONArray();
        arr.put(new JSONObject().put("id", 1).put("name", "Favorites").put("entries", 12));
        return arr;
    }

    public JSONObject getUpdatesStatusJson() throws JSONException {
        JSONObject active = updateBundleRepository.getActiveBundle();
        String activeVersion = active == null ? "bundled" : active.optString("resource_version", "bundled");
        return new JSONObject()
                .put("app_version", "0.1.0-stage1")
                .put("active_bundle_version", activeVersion)
                .put("fallback_bundle_version", "bundled")
                .put("manifest_url", manifestUrl)
                .put("last_check", "unknown");
    }

    public JSONObject maintenanceAction(String action) throws JSONException {
        logRepository.addLog("INFO", "Maintenance action: " + action);
        if ("rescan-library".equals(action)) {
            try {
                int n = libraryIndexer.rescanFromProfiles(appContext, profileRepository, storageBrowser);
                logRepository.addLog("INFO", "Library rescan indexed " + n + " audio files");
                return new JSONObject().put("ok", true).put("action", action).put("indexed", n);
            } catch (Exception e) {
                logRepository.addLog("ERROR", "Library rescan failed: " + e.getMessage());
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
