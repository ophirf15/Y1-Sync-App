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

  return {
    status: function () { return get("/api/status"); },
    deviceInfo: function () { return get("/api/device-info"); },
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
    libraryItems: function () { return get("/api/library/items"); },
    libraryRescan: function () { return post("/api/library/rescan"); },
    playlists: function () { return get("/api/playlists"); },
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
