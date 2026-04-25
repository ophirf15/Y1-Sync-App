package io.innoasis.y1syncer.smb;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import jcifs.CIFSContext;
import jcifs.smb.SmbFile;

/**
 * Lists SMB shares (at the host root) or directories inside a share using jcifs-ng.
 * Used by the web console so users can pick a folder without typing paths by hand.
 */
public final class SmbBrowser {

    private SmbBrowser() {
    }

    public static JSONObject browse(JSONObject req) throws JSONException {
        String host = req.optString("host", "").trim();
        if (host.isEmpty()) {
            return failure("host is required");
        }
        int port = optPort(req);
        String domain = req.optString("domain", "");
        String user = req.optString("username", "");
        String pass = req.optString("password", "");
        String share = req.optString("share_name", "").trim();
        String pathInside = normalizePath(req.optString("path", ""));

        CIFSContext ctx;
        try {
            ctx = SmbCifsContexts.forUser(domain, user, pass);
        } catch (Exception e) {
            return failure("SMB client init failed: " + summarize(e));
        }

        String url;
        try {
            url = buildUrl(host, port, share, pathInside);
        } catch (IllegalArgumentException e) {
            return failure(e.getMessage());
        }

        JSONArray entries = new JSONArray();
        try {
            SmbFile dir = new SmbFile(url, ctx);
            SmbFile[] children = dir.listFiles();
            if (children != null) {
                for (SmbFile child : children) {
                    String name = child.getName();
                    if (name == null) {
                        continue;
                    }
                    name = stripTrailingSlash(name);
                    if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
                        continue;
                    }
                    if (share.isEmpty() && isAdminShare(name)) {
                        continue;
                    }
                    boolean isDir = child.isDirectory();
                    if (!share.isEmpty() && !isDir) {
                        continue;
                    }
                    JSONObject row = new JSONObject();
                    row.put("name", name);
                    row.put("directory", isDir);
                    if (share.isEmpty()) {
                        row.put("type", "share");
                        row.put("relative", name);
                    } else {
                        row.put("type", "dir");
                        String rel = pathInside.isEmpty() ? name : pathInside + "/" + name;
                        row.put("relative", rel);
                    }
                    entries.put(row);
                }
            }
        } catch (Exception e) {
            return failure(summarize(e));
        }
        return success(entries, share, pathInside);
    }

    private static String summarize(Throwable e) {
        String m = e.getMessage();
        if (m != null && m.trim().length() > 0) {
            return m;
        }
        Throwable c = e.getCause();
        if (c != null && c.getMessage() != null && c.getMessage().trim().length() > 0) {
            return c.getMessage();
        }
        return e.getClass().getSimpleName();
    }

    private static boolean isAdminShare(String name) {
        String u = name.toUpperCase();
        return "IPC$".equals(u) || "ADMIN$".equals(u);
    }

    private static String normalizePath(String pathInside) {
        String p = pathInside.trim().replace('\\', '/');
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    private static String buildUrl(String host, int port, String share, String pathInside) {
        StringBuilder sb = new StringBuilder();
        sb.append("smb://").append(host).append(":").append(port).append("/");
        if (share.isEmpty()) {
            return sb.toString();
        }
        sb.append(SmbUrlPaths.validateSegment(share)).append("/");
        if (!pathInside.isEmpty()) {
            for (String seg : pathInside.split("/")) {
                if (seg.isEmpty()) {
                    continue;
                }
                sb.append(SmbUrlPaths.validateSegment(seg)).append("/");
            }
        }
        return sb.toString();
    }

    private static int optPort(JSONObject o) {
        try {
            int v = o.getInt("port");
            return v > 0 ? v : 445;
        } catch (JSONException e) {
            try {
                String s = o.optString("port", "445").trim();
                if (s.isEmpty()) {
                    return 445;
                }
                int v = Integer.parseInt(s);
                return v > 0 ? v : 445;
            } catch (NumberFormatException nfe) {
                return 445;
            }
        }
    }

    private static String stripTrailingSlash(String s) {
        if (s != null && s.endsWith("/")) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static JSONObject failure(String msg) throws JSONException {
        return new JSONObject().put("ok", false).put("message", msg).put("entries", new JSONArray());
    }

    private static JSONObject success(JSONArray entries, String share, String path) throws JSONException {
        return new JSONObject()
                .put("ok", true)
                .put("share", share)
                .put("path", path)
                .put("entries", entries);
    }
}
