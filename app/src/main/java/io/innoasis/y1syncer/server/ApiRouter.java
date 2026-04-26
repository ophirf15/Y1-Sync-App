package io.innoasis.y1syncer.server;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.innoasis.y1syncer.runtime.CoreRuntimeController;

public class ApiRouter {
    private static final Pattern PROFILE_ID_PATTERN = Pattern.compile("^/api/profiles/(\\d+)$");
    private static final Pattern PROFILE_ACTION_PATTERN = Pattern.compile("^/api/profiles/(\\d+)/(duplicate|enable|disable|test-connection|sync-now)$");
    private static final Pattern LIBRARY_ITEM_ID_PATTERN = Pattern.compile("^/api/library/items/(\\d+)$");
    private static final Pattern PLAYLIST_ID_PATTERN = Pattern.compile("^/api/playlists/(\\d+)$");
    private static final Pattern PLAYLIST_DUPLICATE_PATTERN = Pattern.compile("^/api/playlists/(\\d+)/duplicate$");
    private static final Pattern PLAYLIST_ENTRIES_PATTERN = Pattern.compile("^/api/playlists/(\\d+)/entries$");
    private static final Pattern PLAYLIST_ENTRY_ID_PATTERN = Pattern.compile("^/api/playlists/(\\d+)/entries/(\\d+)$");
    private static final Pattern PLAYLIST_REORDER_PATTERN = Pattern.compile("^/api/playlists/(\\d+)/entries/reorder$");
    private static final Pattern PLAYLIST_EXPORT_PATTERN = Pattern.compile("^/api/playlists/(\\d+)/export\\.m3u8$");

    private final CoreRuntimeController runtimeController;

    public ApiRouter(CoreRuntimeController runtimeController) {
        this.runtimeController = runtimeController;
    }

    public ApiResponse handle(String method, String uri, Map<String, String> queryParams, String requestBody) {
        try {
            JSONObject body = parseBody(requestBody);
            if ("/api/status".equals(uri)) {
                return new ApiResponse(200, runtimeController.getStatusJson().toString());
            }
            if ("/api/device-info".equals(uri)) {
                return new ApiResponse(200, runtimeController.getDeviceInfoJson().toString());
            }
            if ("/api/settings".equals(uri) && "GET".equals(method)) {
                return new ApiResponse(200, runtimeController.getSettingsJson().toString());
            }
            if ("/api/settings/manifest".equals(uri) && "POST".equals(method)) {
                return new ApiResponse(200, runtimeController.setManifestUrlSetting(body).toString());
            }
            if ("/api/settings/server-port".equals(uri) && "POST".equals(method)) {
                return new ApiResponse(200, runtimeController.setServerPortSetting(body).toString());
            }
            if ("/api/settings/auto-sync".equals(uri) && "POST".equals(method)) {
                return new ApiResponse(200, runtimeController.setAutoSyncSetting(body).toString());
            }
            if ("/api/sync/now".equals(uri) && "POST".equals(method)) {
                runtimeController.syncNow("api");
                return new ApiResponse(200, new JSONObject().put("accepted", true).toString());
            }
            if ("/api/profiles".equals(uri) && "GET".equals(method)) {
                return new ApiResponse(200, runtimeController.getProfilesJson().toString());
            }
            if ("/api/profiles".equals(uri) && "POST".equals(method)) {
                return new ApiResponse(200, runtimeController.createProfile(body).toString());
            }
            Matcher profileIdMatcher = PROFILE_ID_PATTERN.matcher(uri);
            if (profileIdMatcher.matches()) {
                long id = Long.parseLong(profileIdMatcher.group(1));
                if ("GET".equals(method)) {
                    return new ApiResponse(200, runtimeController.getProfileJson(id).toString());
                }
                if ("PUT".equals(method)) {
                    return new ApiResponse(200, runtimeController.updateProfile(id, body).toString());
                }
                if ("DELETE".equals(method)) {
                    return new ApiResponse(200, runtimeController.deleteProfile(id).toString());
                }
            }
            Matcher actionMatcher = PROFILE_ACTION_PATTERN.matcher(uri);
            if (actionMatcher.matches() && "POST".equals(method)) {
                long id = Long.parseLong(actionMatcher.group(1));
                String action = actionMatcher.group(2);
                if ("duplicate".equals(action)) {
                    return new ApiResponse(200, runtimeController.duplicateProfile(id).toString());
                }
                if ("enable".equals(action)) {
                    return new ApiResponse(200, runtimeController.setProfileActive(id, true).toString());
                }
                if ("disable".equals(action)) {
                    return new ApiResponse(200, runtimeController.setProfileActive(id, false).toString());
                }
                if ("test-connection".equals(action)) {
                    return new ApiResponse(200, runtimeController.testProfileConnection(id, body).toString());
                }
                if ("sync-now".equals(action)) {
                    return new ApiResponse(200, runtimeController.syncProfileNow(id).toString());
                }
            }
            if ("/api/sync/status".equals(uri)) {
                return new ApiResponse(200, runtimeController.getSyncStatusJson().toString());
            }
            if ("/api/sync/runs".equals(uri)) {
                return new ApiResponse(200, runtimeController.getSyncRunsJson().toString());
            }
            if ("/api/library/scan-status".equals(uri)) {
                return new ApiResponse(200, runtimeController.getLibraryScanStatusJson().toString());
            }
            if ("/api/library/items".equals(uri) && "GET".equals(method)) {
                return new ApiResponse(200, runtimeController.getLibraryItemsJson(queryParams).toString());
            }
            if ("/api/library/duplicates".equals(uri) && "GET".equals(method)) {
                return new ApiResponse(200, runtimeController.getLibraryDuplicatesJson().toString());
            }
            Matcher libDel = LIBRARY_ITEM_ID_PATTERN.matcher(uri);
            if (libDel.matches() && "DELETE".equals(method)) {
                long mid = Long.parseLong(libDel.group(1));
                return new ApiResponse(200, runtimeController.deleteLibraryItem(mid).toString());
            }
            if ("/api/library/rescan".equals(uri) && "POST".equals(method)) {
                return new ApiResponse(200, runtimeController.maintenanceAction("rescan-library").toString());
            }
            if ("/api/library/reindex-metadata".equals(uri) && "POST".equals(method)) {
                return new ApiResponse(200, runtimeController.reindexLibraryMetadata().toString());
            }
            if ("/api/playlists".equals(uri) && "GET".equals(method)) {
                return new ApiResponse(200, runtimeController.getPlaylistsJson().toString());
            }
            if ("/api/playlists".equals(uri) && "POST".equals(method)) {
                return new ApiResponse(200, runtimeController.createPlaylist(body).toString());
            }
            Matcher plExport = PLAYLIST_EXPORT_PATTERN.matcher(uri);
            if (plExport.matches() && "GET".equals(method)) {
                long pid = Long.parseLong(plExport.group(1));
                String m3u = runtimeController.exportPlaylistM3u8(pid);
                return new ApiResponse(200, m3u, "application/vnd.apple.mpegurl");
            }
            Matcher plDup = PLAYLIST_DUPLICATE_PATTERN.matcher(uri);
            if (plDup.matches() && "POST".equals(method)) {
                long pid = Long.parseLong(plDup.group(1));
                return new ApiResponse(200, runtimeController.duplicatePlaylist(pid).toString());
            }
            Matcher plReorder = PLAYLIST_REORDER_PATTERN.matcher(uri);
            if (plReorder.matches() && "PUT".equals(method)) {
                long pid = Long.parseLong(plReorder.group(1));
                return new ApiResponse(200, runtimeController.reorderPlaylistEntries(pid, body).toString());
            }
            Matcher plEntryDel = PLAYLIST_ENTRY_ID_PATTERN.matcher(uri);
            if (plEntryDel.matches() && "DELETE".equals(method)) {
                long entryId = Long.parseLong(plEntryDel.group(2));
                return new ApiResponse(200, runtimeController.removePlaylistEntry(entryId).toString());
            }
            Matcher plEntries = PLAYLIST_ENTRIES_PATTERN.matcher(uri);
            if (plEntries.matches()) {
                long pid = Long.parseLong(plEntries.group(1));
                if ("GET".equals(method)) {
                    return new ApiResponse(200, runtimeController.getPlaylistEntriesJson(pid).toString());
                }
                if ("POST".equals(method)) {
                    return new ApiResponse(200, runtimeController.addPlaylistTracks(pid, body).toString());
                }
            }
            Matcher plId = PLAYLIST_ID_PATTERN.matcher(uri);
            if (plId.matches()) {
                long pid = Long.parseLong(plId.group(1));
                if ("GET".equals(method)) {
                    return new ApiResponse(200, runtimeController.getPlaylistJson(pid).toString());
                }
                if ("PUT".equals(method)) {
                    return new ApiResponse(200, runtimeController.updatePlaylist(pid, body).toString());
                }
                if ("DELETE".equals(method)) {
                    return new ApiResponse(200, runtimeController.deletePlaylist(pid).toString());
                }
            }
            if ("/api/updates/status".equals(uri) && "GET".equals(method)) {
                return new ApiResponse(200, runtimeController.getUpdatesStatusJson().toString());
            }
            if ("/api/updates/check".equals(uri) && "POST".equals(method)) {
                JSONObject json = runtimeController.checkForBundleUpdates();
                return new ApiResponse(200, json.toString());
            }
            if ("/api/updates/download-apply".equals(uri) && "POST".equals(method)) {
                return new ApiResponse(200, runtimeController.downloadAndApplyBundleUpdate().toString());
            }
            if ("/api/updates/restart-server".equals(uri) && "POST".equals(method)) {
                return new ApiResponse(200, runtimeController.restartServerForUpdates().toString());
            }
            if ("/api/maintenance/rebuild-sync-index".equals(uri) && "POST".equals(method)) {
                return new ApiResponse(200, runtimeController.maintenanceAction("rebuild-sync-index").toString());
            }
            if ("/api/maintenance/clean-part-files".equals(uri) && "POST".equals(method)) {
                return new ApiResponse(200, runtimeController.maintenanceAction("clean-part-files").toString());
            }
            if ("/api/maintenance/prune-empty-folders".equals(uri) && "POST".equals(method)) {
                return new ApiResponse(200, runtimeController.maintenanceAction("prune-empty-folders").toString());
            }
            if ("/api/maintenance/failed-downloads".equals(uri) && "GET".equals(method)) {
                return new ApiResponse(200, runtimeController.getFailedDownloadsJson().toString());
            }
            if ("/api/maintenance/incomplete-downloads".equals(uri) && "GET".equals(method)) {
                return new ApiResponse(200, runtimeController.getIncompleteDownloadsJson().toString());
            }
            if ("/api/updates/revert-bundled".equals(uri) && "POST".equals(method)) {
                runtimeController.revertBundledUi();
                return new ApiResponse(200, new JSONObject().put("reverted", true).toString());
            }
            if ("/api/logs".equals(uri)) {
                JSONArray logs = runtimeController.getLogsJson();
                return new ApiResponse(200, logs.toString());
            }
            if ("/api/smb/browse".equals(uri) && "POST".equals(method)) {
                return new ApiResponse(200, runtimeController.smbBrowse(body).toString());
            }
            if ("/api/storage/roots".equals(uri) && "GET".equals(method)) {
                return new ApiResponse(200, runtimeController.getStorageRootsJson().toString());
            }
            if ("/api/storage/list".equals(uri) && "GET".equals(method)) {
                return new ApiResponse(200, runtimeController.getStorageChildrenJson(queryParams).toString());
            }
            return new ApiResponse(404, new JSONObject().put("error", "Not found").toString());
        } catch (JSONException e) {
            return new ApiResponse(500, "{\"error\":\"json\"}");
        } catch (Exception e) {
            try {
                return new ApiResponse(500, new JSONObject().put("error", e.getMessage() == null ? "server" : e.getMessage()).toString());
            } catch (JSONException je) {
                return new ApiResponse(500, "{\"error\":\"server\"}");
            }
        } catch (Throwable t) {
            try {
                String m = t.getMessage();
                return new ApiResponse(500, new JSONObject()
                        .put("error", (m == null || m.isEmpty()) ? t.getClass().getSimpleName() : m)
                        .put("type", t.getClass().getName())
                        .toString());
            } catch (JSONException je) {
                return new ApiResponse(500, "{\"error\":\"server\"}");
            }
        }
    }

    private JSONObject parseBody(String requestBody) {
        if (requestBody == null || requestBody.trim().length() == 0) {
            return new JSONObject();
        }
        try {
            return new JSONObject(requestBody);
        } catch (JSONException ignored) {
            return new JSONObject();
        }
    }
}
