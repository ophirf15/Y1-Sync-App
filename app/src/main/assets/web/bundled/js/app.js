window.y1Ui = (function () {
  var topPollTimer = null;

  function setActiveNav(page) {
    var links = document.querySelectorAll("[data-nav]");
    for (var i = 0; i < links.length; i++) {
      if (links[i].getAttribute("data-nav") === page) {
        links[i].className = "active";
      }
    }
  }

  function renderTopStatus() {
    if (!window.y1Api) return;
    function pullOnce() {
      Promise.all([window.y1Api.status(), window.y1Api.syncStatus()]).then(function (vals) {
      var status = vals[0] || {};
      var sync = vals[1] || {};
      var el = document.getElementById("ipHeader");
      if (el) {
        el.textContent = "http://" + status.ip + ":" + status.port;
      }
      var s = document.getElementById("statusHeader");
      if (s) {
        var live = status.last_sync || "";
        if (sync && sync.state === "running") {
          live = "Syncing " + (sync.current_index || 0) + "/" + (sync.total_files || 0) + ": " + (sync.current_file || "");
        } else if (sync && sync.state === "error" && sync.last_error) {
          live = "Error: " + sync.last_error;
        } else if (sync && sync.state === "done" && sync.summary) {
          live = sync.summary;
        }
        s.textContent = "Profile: " + status.profile + " | Last sync: " + live;
      }
      });
    }
    pullOnce();
    if (topPollTimer) {
      clearInterval(topPollTimer);
    }
    topPollTimer = setInterval(pullOnce, 1500);
    if (typeof window !== "undefined") {
      window.onbeforeunload = function () {
        if (topPollTimer) clearInterval(topPollTimer);
      };
    }
  }

  function toTableRows(items, fields) {
    var html = "";
    for (var i = 0; i < items.length; i++) {
      html += "<tr>";
      for (var f = 0; f < fields.length; f++) {
        var v = items[i][fields[f]];
        html += "<td>" + (v === undefined ? "" : String(v)) + "</td>";
      }
      html += "</tr>";
    }
    return html || "<tr><td colspan='" + fields.length + "' class='muted'>No data</td></tr>";
  }

  return {
    setActiveNav: setActiveNav,
    renderTopStatus: renderTopStatus,
    toTableRows: toTableRows
  };
})();
