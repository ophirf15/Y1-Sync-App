package io.innoasis.y1syncer.updates;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Reads latest release metadata from GitHub's public API (no token).
 */
public final class GitHubReleaseChecker {

    public static final String REPO_LATEST = "https://api.github.com/repos/ophirf15/Y1-Sync-App/releases/latest";

    private GitHubReleaseChecker() {
    }

    public static JSONObject fetchLatestRelease() throws JSONException {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(REPO_LATEST).openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            int code = conn.getResponseCode();
            InputStream in = new BufferedInputStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            try {
                StringBuilder sb = new StringBuilder();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    sb.append(new String(buf, 0, n, "UTF-8"));
                }
                String raw = sb.toString();
                if (code >= 400) {
                    return new JSONObject().put("error", "HTTP " + code).put("body", raw.length() > 500 ? raw.substring(0, 500) : raw);
                }
                JSONObject rel = new JSONObject(raw);
                JSONObject out = new JSONObject();
                out.put("tag_name", rel.optString("tag_name", ""));
                out.put("name", rel.optString("name", ""));
                out.put("html_url", rel.optString("html_url", ""));
                out.put("published_at", rel.optString("published_at", ""));
                String apkUrl = "";
                JSONArray assets = rel.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.optJSONObject(i);
                        if (a == null) {
                            continue;
                        }
                        String name = a.optString("name", "").toLowerCase();
                        if (name.endsWith(".apk")) {
                            apkUrl = a.optString("browser_download_url", "");
                            break;
                        }
                    }
                }
                out.put("apk_download_url", apkUrl);
                return out;
            } finally {
                in.close();
                conn.disconnect();
            }
        } catch (Exception e) {
            return new JSONObject().put("error", e.getMessage() == null ? "fetch failed" : e.getMessage());
        }
    }

    /**
     * True if {@code tagName} version (e.g. {@code v0.2.0}) is numerically newer than {@code currentVersionName}.
     */
    public static boolean isNewerThanCurrent(String tagName, String currentVersionName) {
        int[] a = parseVersionTriplet(stripV(tagName));
        int[] b = parseVersionTriplet(stripV(currentVersionName));
        for (int i = 0; i < 3; i++) {
            if (a[i] > b[i]) {
                return true;
            }
            if (a[i] < b[i]) {
                return false;
            }
        }
        return false;
    }

    private static String stripV(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        int dash = t.indexOf('-');
        if (dash > 0) {
            t = t.substring(0, dash);
        }
        if (t.startsWith("v") || t.startsWith("V")) {
            t = t.substring(1);
        }
        return t.trim();
    }

    private static int[] parseVersionTriplet(String s) {
        int[] out = new int[]{0, 0, 0};
        if (s == null || s.length() == 0) {
            return out;
        }
        String[] parts = s.split("\\.");
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            String p = parts[i].replaceAll("[^0-9].*", "");
            if (p.length() > 0) {
                try {
                    out[i] = Integer.parseInt(p);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }
}
