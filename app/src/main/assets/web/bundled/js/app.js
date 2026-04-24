window.y1Ui = (function () {
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
    window.y1Api.status().then(function (status) {
      var el = document.getElementById("ipHeader");
      if (el) {
        el.textContent = "http://" + status.ip + ":" + status.port;
      }
      var s = document.getElementById("statusHeader");
      if (s) {
        s.textContent = "Profile: " + status.profile + " | Last sync: " + status.last_sync;
      }
    });
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
