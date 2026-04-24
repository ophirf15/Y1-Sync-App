package io.innoasis.y1syncer.server;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.innoasis.y1syncer.runtime.CoreRuntimeController;

public class ApiRouter {
    private final CoreRuntimeController runtimeController;

    public ApiRouter(CoreRuntimeController runtimeController) {
        this.runtimeController = runtimeController;
    }

    public ApiResponse handle(String method, String uri) {
        try {
            if ("/api/status".equals(uri)) {
                return new ApiResponse(200, runtimeController.getStatusJson().toString());
            }
            if ("/api/device-info".equals(uri)) {
                return new ApiResponse(200, runtimeController.getDeviceInfoJson().toString());
            }
            if ("/api/sync/now".equals(uri) && "POST".equals(method)) {
                runtimeController.syncNow(null);
                return new ApiResponse(200, new JSONObject().put("accepted", true).toString());
            }
            if ("/api/profiles".equals(uri) && "GET".equals(method)) {
                return new ApiResponse(200, runtimeController.getProfilesJson().toString());
            }
            if ("/api/profiles".equals(uri) && "POST".equals(method)) {
                return new ApiResponse(200, runtimeController.createProfile(new JSONObject()).toString());
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
            if ("/api/library/items".equals(uri)) {
                return new ApiResponse(200, runtimeController.getLibraryItemsJson().toString());
            }
            if ("/api/library/rescan".equals(uri) && "POST".equals(method)) {
                return new ApiResponse(200, runtimeController.maintenanceAction("rescan-library").toString());
            }
            if ("/api/playlists".equals(uri) && "GET".equals(method)) {
                return new ApiResponse(200, runtimeController.getPlaylistsJson().toString());
            }
            if ("/api/updates/status".equals(uri) && "GET".equals(method)) {
                return new ApiResponse(200, runtimeController.getUpdatesStatusJson().toString());
            }
            if ("/api/updates/check".equals(uri) && "POST".equals(method)) {
                JSONObject json = runtimeController.checkForBundleUpdates();
                return new ApiResponse(200, json.toString());
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
                return new ApiResponse(200, new JSONArray().toString());
            }
            if ("/api/maintenance/incomplete-downloads".equals(uri) && "GET".equals(method)) {
                return new ApiResponse(200, new JSONArray().toString());
            }
            if ("/api/updates/revert-bundled".equals(uri) && "POST".equals(method)) {
                runtimeController.revertBundledUi();
                return new ApiResponse(200, new JSONObject().put("reverted", true).toString());
            }
            if ("/api/logs".equals(uri)) {
                JSONArray logs = runtimeController.getLogsJson();
                return new ApiResponse(200, logs.toString());
            }
            return new ApiResponse(404, new JSONObject().put("error", "Not found").toString());
        } catch (JSONException e) {
            return new ApiResponse(500, "{\"error\":\"json\"}");
        }
    }
}
