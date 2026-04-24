window.y1Api = (function () {
  function get(path) {
    return fetch(path).then(function (r) { return r.json(); });
  }

  function post(path) {
    return fetch(path, { method: "POST" }).then(function (r) { return r.json(); });
  }

  return {
    status: function () { return get("/api/status"); },
    deviceInfo: function () { return get("/api/device-info"); },
    profiles: function () { return get("/api/profiles"); },
    createProfile: function () { return post("/api/profiles"); },
    syncNow: function () { return post("/api/sync/now"); },
    syncStatus: function () { return get("/api/sync/status"); },
    syncRuns: function () { return get("/api/sync/runs"); },
    libraryItems: function () { return get("/api/library/items"); },
    libraryRescan: function () { return post("/api/library/rescan"); },
    playlists: function () { return get("/api/playlists"); },
    updatesStatus: function () { return get("/api/updates/status"); },
    checkUpdates: function () { return post("/api/updates/check"); },
    revertBundled: function () { return post("/api/updates/revert-bundled"); },
    logs: function () { return get("/api/logs"); },
    maintenance: function (action) { return post("/api/maintenance/" + action); },
    failedDownloads: function () { return get("/api/maintenance/failed-downloads"); },
    incompleteDownloads: function () { return get("/api/maintenance/incomplete-downloads"); }
  };
})();
