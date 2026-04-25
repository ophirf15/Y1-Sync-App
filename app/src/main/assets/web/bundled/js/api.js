window.y1Api = (function () {
  function get(path) {
    return fetch(path).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (j) {
        if (!r.ok) {
          if (j.error && !j.message) j.message = j.error;
          if (!j.message) j.message = "HTTP " + r.status;
        }
        return j;
      });
    });
  }

  function send(path, method, payload) {
    var opts = { method: method };
    if (payload) {
      opts.headers = { "Content-Type": "application/json" };
      opts.body = JSON.stringify(payload);
    }
    return fetch(path, opts).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (j) {
        if (!r.ok) {
          if (j.error && !j.message) j.message = j.error;
          if (!j.message) j.message = "HTTP " + r.status;
          if (j.ok !== false) j.ok = false;
        }
        return j;
      });
    });
  }

  function post(path, payload) {
    return send(path, "POST", payload);
  }

  function put(path, payload) {
    return send(path, "PUT", payload);
  }

  function del(path) {
    return send(path, "DELETE");
  }

  function q(v) {
    return encodeURIComponent(v || "");
  }

  function queryString(obj) {
    if (!obj) return "";
    var parts = [];
    for (var k in obj) {
      if (obj.hasOwnProperty(k) && obj[k] !== undefined && obj[k] !== null && String(obj[k]).length) {
        parts.push(encodeURIComponent(k) + "=" + encodeURIComponent(String(obj[k])));
      }
    }
    return parts.length ? "?" + parts.join("&") : "";
  }

  return {
    status: function () { return get("/api/status"); },
    deviceInfo: function () { return get("/api/device-info"); },
    settings: function () { return get("/api/settings"); },
    setManifestUrl: function (url) { return post("/api/settings/manifest", { url: url }); },
    setServerPort: function (port) { return post("/api/settings/server-port", { port: port }); },
    setAutoSync: function (enabled) { return post("/api/settings/auto-sync", { enabled: !!enabled }); },
    profiles: function () { return get("/api/profiles"); },
    profile: function (id) { return get("/api/profiles/" + id); },
    createProfile: function (payload) { return post("/api/profiles", payload || {}); },
    updateProfile: function (id, payload) { return put("/api/profiles/" + id, payload || {}); },
    duplicateProfile: function (id) { return post("/api/profiles/" + id + "/duplicate"); },
    enableProfile: function (id) { return post("/api/profiles/" + id + "/enable"); },
    disableProfile: function (id) { return post("/api/profiles/" + id + "/disable"); },
    deleteProfile: function (id) { return del("/api/profiles/" + id); },
    testProfile: function (id, payload) { return post("/api/profiles/" + id + "/test-connection", payload || {}); },
    syncProfileNow: function (id) { return post("/api/profiles/" + id + "/sync-now"); },
    syncNow: function () { return post("/api/sync/now"); },
    syncStatus: function () { return get("/api/sync/status"); },
    syncRuns: function () { return get("/api/sync/runs"); },
    libraryItems: function (query) { return get("/api/library/items" + queryString(query)); },
    libraryDeleteItem: function (id) { return del("/api/library/items/" + id); },
    libraryRescan: function () { return post("/api/library/rescan"); },
    libraryReindexMetadata: function () { return post("/api/library/reindex-metadata"); },
    playlists: function () { return get("/api/playlists"); },
    createPlaylist: function (name) { return post("/api/playlists", { name: name }); },
    playlist: function (id) { return get("/api/playlists/" + id); },
    updatePlaylist: function (id, payload) { return put("/api/playlists/" + id, payload || {}); },
    deletePlaylist: function (id) { return del("/api/playlists/" + id); },
    duplicatePlaylist: function (id) { return post("/api/playlists/" + id + "/duplicate"); },
    playlistEntries: function (id) { return get("/api/playlists/" + id + "/entries"); },
    addPlaylistTrack: function (id, path) { return post("/api/playlists/" + id + "/entries", { path: path }); },
    addPlaylistTracksBulk: function (id, paths) { return post("/api/playlists/" + id + "/entries", { paths: paths || [] }); },
    removePlaylistEntry: function (playlistId, entryId) { return del("/api/playlists/" + playlistId + "/entries/" + entryId); },
    reorderPlaylist: function (id, orderIds) { return put("/api/playlists/" + id + "/entries/reorder", { order: orderIds }); },
    playlistExportUrl: function (id) { return "/api/playlists/" + id + "/export.m3u8"; },
    updatesStatus: function () { return get("/api/updates/status"); },
    checkUpdates: function () { return post("/api/updates/check"); },
    applyUpdate: function () { return post("/api/updates/download-apply"); },
    restartServer: function () { return post("/api/updates/restart-server"); },
    revertBundled: function () { return post("/api/updates/revert-bundled"); },
    smbBrowse: function (payload) { return post("/api/smb/browse", payload || {}); },
    storageRoots: function () { return get("/api/storage/roots"); },
    storageList: function (root, path) { return get("/api/storage/list?root=" + q(root) + "&path=" + q(path)); },
    logs: function () { return get("/api/logs"); },
    maintenance: function (action) { return post("/api/maintenance/" + action); },
    failedDownloads: function () { return get("/api/maintenance/failed-downloads"); },
    incompleteDownloads: function () { return get("/api/maintenance/incomplete-downloads"); }
  };
})();
