package io.innoasis.y1syncer.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

import io.innoasis.y1syncer.BuildConfig;
import io.innoasis.y1syncer.db.Y1DatabaseHelper;
import io.innoasis.y1syncer.db.repos.LogRepository;
import io.innoasis.y1syncer.db.repos.PlaylistRepository;
import io.innoasis.y1syncer.db.repos.ProfileRepository;
import io.innoasis.y1syncer.db.repos.SyncStateRepository;
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
import io.innoasis.y1syncer.sync.SyncProgressListener;
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
    private static final long STALE_SYNC_TIMEOUT_MS = 10 * 60 * 1000L;
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
    private final SyncStateRepository syncStateRepository;
    private final BundleStorage bundleStorage;
    private final WebBundleUpdateManager webBundleUpdateManager;
    private final SyncOrchestrator syncOrchestrator;
    private final SyncAlarmScheduler syncAlarmScheduler;
    private final StorageBrowser storageBrowser;
    private final LibraryIndexer libraryIndexer;

    private EmbeddedHttpServer server;
    private PowerManager.WakeLock serverWakeLock;
    private WifiManager.WifiLock serverWifiLock;
    private int serverPort;
    private boolean autoSyncEnabled;
    private String lastSyncStatus = "Never synced";
    private String manifestUrl;
    private final SyncStatusState syncStatus = new SyncStatusState();

    public CoreRuntimeController(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.dbHelper = new Y1DatabaseHelper(appContext);
        this.profileRepository = new ProfileRepository(dbHelper);
        this.logRepository = new LogRepository(dbHelper);
        this.updateBundleRepository = new UpdateBundleRepository(dbHelper);
        this.playlistRepository = new PlaylistRepository(dbHelper);
        this.syncStateRepository = new SyncStateRepository(dbHelper);
        this.bundleStorage = new BundleStorage(appContext, updateBundleRepository);
        this.webBundleUpdateManager = new WebBundleUpdateManager(bundleStorage, appContext, updateBundleRepository, logRepository);
        this.storageBrowser = new StorageBrowser(appContext);
        this.libraryIndexer = new LibraryIndexer(dbHelper);
        this.syncOrchestrator = new SyncOrchestrator(appContext, logRepository, profileRepository, storageBrowser, libraryIndexer, syncStateRepository, new SyncProgressListener() {
            @Override
            public void onSyncStart(long profileId, String profileName, int totalFiles, long totalBytes) {
                synchronized (syncStatus) {
                    syncStatus.state = "running";
                    if (syncStatus.triggerType == null) {
                        syncStatus.triggerType = "";
                    }
                    syncStatus.profileId = profileId;
                    syncStatus.profileName = profileName == null ? "" : profileName;
                    syncStatus.totalFiles = totalFiles;
                    syncStatus.currentIndex = 0;
                    syncStatus.currentFile = "";
                    syncStatus.bytesDone = 0L;
                    syncStatus.bytesTotal = totalBytes;
                    syncStatus.downloadedFiles = 0;
                    syncStatus.skippedFiles = 0;
                    syncStatus.failedFiles = 0;
                    syncStatus.startedAt = System.currentTimeMillis();
                    syncStatus.updatedAt = syncStatus.startedAt;
                    syncStatus.lastError = "";
                    syncStatus.failedDetails = "";
                    syncStatus.summary = "";
                }
            }

            @Override
            public void onFileStart(String remotePath, int index, int total, long fileSize) {
                synchronized (syncStatus) {
                    syncStatus.currentFile = remotePath == null ? "" : remotePath;
                    syncStatus.currentIndex = index;
                    syncStatus.totalFiles = total;
                    syncStatus.updatedAt = System.currentTimeMillis();
                }
            }

            @Override
            public void onFileResult(String remotePath, boolean downloaded, boolean skipped, String errorMessage, long cumulativeBytesDone) {
                synchronized (syncStatus) {
                    syncStatus.bytesDone = cumulativeBytesDone;
                    if (downloaded) {
                        syncStatus.downloadedFiles++;
                    }
                    if (skipped) {
                        syncStatus.skippedFiles++;
                    }
                    if (!downloaded && !skipped) {
                        syncStatus.failedFiles++;
                        if (errorMessage != null && errorMessage.length() > 0) {
                            syncStatus.lastError = errorMessage;
                            if (syncStatus.failedDetails.length() < 3000) {
                                if (syncStatus.failedDetails.length() > 0) {
                                    syncStatus.failedDetails += "\n";
                                }
                                syncStatus.failedDetails += (remotePath == null ? "<unknown>" : remotePath) + " :: " + errorMessage;
                            }
                        }
                    }
                    syncStatus.updatedAt = System.currentTimeMillis();
                }
            }

            @Override
            public void onSyncDone(int attempted, int downloaded, int skipped, int failed, String summary, String errorMessage) {
                synchronized (syncStatus) {
                    syncStatus.state = failed > 0 ? "error" : "done";
                    syncStatus.totalFiles = attempted;
                    syncStatus.downloadedFiles = downloaded;
                    syncStatus.skippedFiles = skipped;
                    syncStatus.failedFiles = failed;
                    syncStatus.summary = summary == null ? "" : summary;
                    syncStatus.updatedAt = System.currentTimeMillis();
                    if (errorMessage != null && errorMessage.length() > 0) {
                        syncStatus.lastError = errorMessage;
                        if (syncStatus.failedDetails.length() == 0) {
                            syncStatus.failedDetails = errorMessage;
                        }
                    }
                }
                syncStateRepository.appendRun(syncStatus.profileId, syncStatus.triggerType == null ? "" : syncStatus.triggerType, syncStatus.startedAt,
                        System.currentTimeMillis(), attempted, downloaded + skipped, failed,
                        errorMessage == null ? "" : errorMessage);
            }
        });
        this.syncAlarmScheduler = new SyncAlarmScheduler(appContext);
        this.serverPort = loadServerPort();
        this.manifestUrl = prefs.getString(KEY_MANIFEST_URL, DEFAULT_MANIFEST_URL);
        this.autoSyncEnabled = prefs.getBoolean(KEY_AUTO_SYNC, false);
        hydrateLastSyncStatusFromHistory();
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
        acquireServerLocks();
        logRepository.addLog("INFO", "Server started on port " + serverPort);
    }

    public void stopServer() {
        if (server == null) {
            return;
        }
        server.stop();
        server = null;
        releaseServerLocks();
        logRepository.addLog("INFO", "Server stopped");
    }

    public void syncNow(String trigger) {
        synchronized (syncStatus) {
            resetStaleRunningSyncLocked(trigger);
            if ("running".equals(syncStatus.state)) {
                lastSyncStatus = "sync already running";
                logRepository.addLog("WARN", "Sync trigger ignored (" + trigger + "): sync already running");
                return;
            }
            syncStatus.state = "running";
            syncStatus.triggerType = trigger;
            syncStatus.updatedAt = System.currentTimeMillis();
            syncStatus.lastError = "";
            syncStatus.failedDetails = "";
        }
        logRepository.addLog("INFO", "Sync requested trigger=" + trigger);
        runSyncAsync(trigger, 0);
    }

    private void runSyncAsync(final String trigger, final long forcedProfileId) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                PowerManager.WakeLock wakeLock = null;
                WifiManager.WifiLock wifiLock = null;
                try {
                    PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
                    if (pm != null) {
                        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "y1syncer:sync");
                        wakeLock.setReferenceCounted(false);
                        wakeLock.acquire();
                    }
                    WifiManager wm = (WifiManager) appContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    if (wm != null) {
                        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "y1syncer:wifi-sync");
                        wifiLock.setReferenceCounted(false);
                        wifiLock.acquire();
                    }
                    logRepository.addLog("INFO", "Sync wake locks acquired (" + trigger + ")");

                    String summary;
                    if (forcedProfileId > 0) {
                        summary = syncOrchestrator.syncProfileById(forcedProfileId);
                    } else {
                        summary = syncOrchestrator.syncNow(null);
                    }
                    lastSyncStatus = "[" + trigger + "] " + summary;
                } catch (Throwable t) {
                    String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                    logRepository.addLog("ERROR", "Sync thread crashed (" + trigger + "): " + msg);
                    synchronized (syncStatus) {
                        syncStatus.state = "error";
                        syncStatus.lastError = msg;
                        syncStatus.summary = "";
                        syncStatus.updatedAt = System.currentTimeMillis();
                    }
                    lastSyncStatus = "[" + trigger + "] error: " + msg;
                } finally {
                    if (wifiLock != null && wifiLock.isHeld()) {
                        wifiLock.release();
                    }
                    if (wakeLock != null && wakeLock.isHeld()) {
                        wakeLock.release();
                    }
                    logRepository.addLog("INFO", "Sync wake locks released (" + trigger + ")");
                }
            }
        }, "sync-runner");
        t.start();
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
        s.currentProfile = resolveCurrentProfileName();
        synchronized (syncStatus) {
            if ("running".equals(syncStatus.state)) {
                s.lastSyncStatus = "Syncing " + syncStatus.currentIndex + "/" + syncStatus.totalFiles + ": " + syncStatus.currentFile;
            } else if ("error".equals(syncStatus.state) && syncStatus.lastError != null && syncStatus.lastError.length() > 0) {
                s.lastSyncStatus = "Error: " + syncStatus.lastError;
            } else if ("done".equals(syncStatus.state) && syncStatus.summary != null && syncStatus.summary.length() > 0) {
                s.lastSyncStatus = syncStatus.summary;
            } else {
                s.lastSyncStatus = lastSyncStatus;
            }
        }
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
        synchronized (syncStatus) {
            resetStaleRunningSyncLocked("profile-" + id);
            if ("running".equals(syncStatus.state)) {
                return new JSONObject().put("accepted", false).put("error", "sync already running");
            }
            syncStatus.state = "running";
            syncStatus.triggerType = "profile-" + id;
            syncStatus.updatedAt = System.currentTimeMillis();
            syncStatus.lastError = "";
            syncStatus.failedDetails = "";
        }
        logRepository.addLog("INFO", "Sync requested trigger=profile-" + id);
        runSyncAsync("profile-" + id, id);
        return new JSONObject().put("accepted", true);
    }

    private void resetStaleRunningSyncLocked(String nextTrigger) {
        if (!"running".equals(syncStatus.state)) {
            return;
        }
        long now = System.currentTimeMillis();
        long lastTouch = syncStatus.updatedAt > 0 ? syncStatus.updatedAt : syncStatus.startedAt;
        if (lastTouch <= 0 || (now - lastTouch) < STALE_SYNC_TIMEOUT_MS) {
            return;
        }
        String staleMsg = "stale sync state reset after " + ((now - lastTouch) / 1000L) + "s without progress";
        logRepository.addLog("WARN", staleMsg + " (next trigger=" + nextTrigger + ")");
        syncStatus.state = "error";
        syncStatus.lastError = staleMsg;
        syncStatus.summary = "";
        syncStatus.currentFile = "";
        syncStatus.updatedAt = now;
        lastSyncStatus = staleMsg;
    }

    public SyncStatusState getSyncStatusStateCopy() {
        synchronized (syncStatus) {
            SyncStatusState c = new SyncStatusState();
            c.state = syncStatus.state;
            c.profileId = syncStatus.profileId;
            c.profileName = syncStatus.profileName;
            c.triggerType = syncStatus.triggerType;
            c.currentFile = syncStatus.currentFile;
            c.currentIndex = syncStatus.currentIndex;
            c.totalFiles = syncStatus.totalFiles;
            c.bytesDone = syncStatus.bytesDone;
            c.bytesTotal = syncStatus.bytesTotal;
            c.startedAt = syncStatus.startedAt;
            c.updatedAt = syncStatus.updatedAt;
            c.downloadedFiles = syncStatus.downloadedFiles;
            c.skippedFiles = syncStatus.skippedFiles;
            c.failedFiles = syncStatus.failedFiles;
            c.lastError = syncStatus.lastError;
            c.failedDetails = syncStatus.failedDetails;
            c.summary = syncStatus.summary;
            return c;
        }
    }

    public JSONObject getSyncStatusJson() throws JSONException {
        synchronized (syncStatus) {
            return new JSONObject()
                    .put("running", "running".equals(syncStatus.state))
                    .put("state", syncStatus.state)
                    .put("profile_id", syncStatus.profileId)
                    .put("profile_name", syncStatus.profileName)
                    .put("trigger", syncStatus.triggerType)
                    .put("current_file", syncStatus.currentFile)
                    .put("current_index", syncStatus.currentIndex)
                    .put("total_files", syncStatus.totalFiles)
                    .put("bytes_done", syncStatus.bytesDone)
                    .put("bytes_total", syncStatus.bytesTotal)
                    .put("downloaded_files", syncStatus.downloadedFiles)
                    .put("skipped_files", syncStatus.skippedFiles)
                    .put("failed_files", syncStatus.failedFiles)
                    .put("started_at", syncStatus.startedAt)
                    .put("updated_at", syncStatus.updatedAt)
                    .put("summary", syncStatus.summary)
                    .put("last_error", syncStatus.lastError)
                    .put("failed_details", syncStatus.failedDetails)
                    .put("last_sync", lastSyncStatus)
                    .put("auto_sync", autoSyncEnabled);
        }
    }

    public JSONArray getSyncRunsJson() throws JSONException {
        return syncStateRepository.getRecentRuns(25);
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

    public JSONArray getLibraryDuplicatesJson() throws JSONException {
        return libraryIndexer.queryDuplicateGroupsJson();
    }

    public JSONObject deleteLibraryItem(long mediaId) throws JSONException {
        String err = libraryIndexer.deleteById(mediaId);
        if (err != null) {
            return new JSONObject().put("ok", false).put("error", err);
        }
        logRepository.addLog("INFO", "Deleted library item id=" + mediaId);
        return new JSONObject().put("ok", true);
    }

    public JSONObject getLibraryArtworkJson(long mediaId) throws JSONException {
        String dataUrl = libraryIndexer.artworkDataUrlByMediaId(mediaId);
        return new JSONObject().put("ok", dataUrl.length() > 0).put("data_url", dataUrl);
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
        JSONObject rel = GitHubReleaseChecker.fetchLatestRelease(appContext);
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
        JSONObject req = body == null ? new JSONObject() : new JSONObject(body.toString());
        long profileId = req.optLong("profile_id", 0L);
        if (profileId > 0) {
            JSONObject p = profileRepository.getProfile(profileId);
            if (p != null) {
                if (req.optString("host", "").trim().length() == 0) {
                    req.put("host", p.optString("host", ""));
                }
                if (req.optString("domain", "").length() == 0) {
                    req.put("domain", p.optString("domain", ""));
                }
                if (req.optString("username", "").length() == 0) {
                    req.put("username", p.optString("username", ""));
                }
                if (req.optString("password", "").length() == 0) {
                    req.put("password", profileRepository.getPasswordEnc(profileId));
                }
                if (req.optString("share_name", "").trim().length() == 0) {
                    req.put("share_name", p.optString("share_name", ""));
                }
                if (!req.has("port") || req.optInt("port", 0) <= 0) {
                    req.put("port", p.optInt("port", 445));
                }
            }
        }
        return SmbBrowser.browse(req);
    }

    private void hydrateLastSyncStatusFromHistory() {
        try {
            JSONArray runs = syncStateRepository.getRecentRuns(1);
            if (runs.length() == 0) {
                return;
            }
            JSONObject r = runs.getJSONObject(0);
            int total = r.optInt("total_files", 0);
            int ok = r.optInt("success_files", 0);
            int fail = r.optInt("failed_files", 0);
            String reason = r.optString("reason", "");
            String trigger = r.optString("trigger", "");
            if (reason.length() > 0) {
                lastSyncStatus = "[" + trigger + "] " + reason;
            } else {
                lastSyncStatus = "[" + trigger + "] files=" + total + " ok=" + ok + " fail=" + fail;
            }
        } catch (Exception ignored) {
            // Keep default "Never synced" if history is unavailable/corrupt.
        }
    }

    private String resolveCurrentProfileName() {
        synchronized (syncStatus) {
            if (syncStatus.profileName != null && syncStatus.profileName.length() > 0) {
                return syncStatus.profileName;
            }
        }
        try {
            String name = profileRepository.pickProfileNameForTriggerSync();
            return name.length() > 0 ? name : "None";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private void acquireServerLocks() {
        try {
            PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            if (pm != null && (serverWakeLock == null || !serverWakeLock.isHeld())) {
                serverWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "y1syncer:server");
                serverWakeLock.setReferenceCounted(false);
                serverWakeLock.acquire();
            }
            WifiManager wm = (WifiManager) appContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && (serverWifiLock == null || !serverWifiLock.isHeld())) {
                serverWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "y1syncer:wifi-server");
                serverWifiLock.setReferenceCounted(false);
                serverWifiLock.acquire();
            }
            logRepository.addLog("INFO", "Server wake locks acquired");
        } catch (Exception e) {
            logRepository.addLog("WARN", "Server wake lock acquire failed: " + e.getMessage());
        }
    }

    private void releaseServerLocks() {
        try {
            if (serverWifiLock != null && serverWifiLock.isHeld()) {
                serverWifiLock.release();
            }
            if (serverWakeLock != null && serverWakeLock.isHeld()) {
                serverWakeLock.release();
            }
            logRepository.addLog("INFO", "Server wake locks released");
        } catch (Exception e) {
            logRepository.addLog("WARN", "Server wake lock release failed: " + e.getMessage());
        }
    }
}
