package io.innoasis.y1syncer.smb;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.UnknownHostException;

import jcifs.CIFSContext;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

/**
 * Performs a real SMB reachability + auth check (open tree / list or exists).
 */
public final class SmbConnectionProbe {

    private SmbConnectionProbe() {
    }

    public static JSONObject probe(JSONObject profile) throws JSONException {
        String host = profile.optString("host", "").trim();
        if (host.isEmpty()) {
            return result(false, "Host is required");
        }
        if (!"SMB".equalsIgnoreCase(profile.optString("protocol", "SMB"))) {
            return result(true, "SMB live test skipped (protocol is not SMB).");
        }
        int port = optPort(profile);
        String domain = profile.optString("domain", "");
        String user = profile.optString("username", "");
        String pass = profile.optString("password", "");
        if (pass.isEmpty()) {
            return result(false, "No password for this test. Type your SMB password in the form (it is not re-displayed after save) or save the profile once with a password.");
        }
        String share = profile.optString("share_name", "").trim();
        String remoteRoot = profile.optString("remote_root_path", "/").trim().replace('\\', '/');
        if (remoteRoot.isEmpty()) {
            remoteRoot = "/";
        }

        CIFSContext ctx;
        try {
            ctx = SmbCifsContexts.forUser(domain, user, pass);
        } catch (Exception e) {
            return result(false, "SMB setup failed: " + summarize(e));
        }

        String url;
        try {
            if (share.isEmpty()) {
                url = "smb://" + host + ":" + port + "/";
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("smb://").append(host).append(":").append(port).append("/");
                sb.append(SmbUrlPaths.validateSegment(share)).append("/");
                sb.append(SmbUrlPaths.pathSuffix(remoteRoot));
                url = sb.toString();
            }
        } catch (IllegalArgumentException e) {
            return result(false, e.getMessage());
        }

        try {
            SmbFile target = new SmbFile(url, ctx);
            if (share.isEmpty()) {
                SmbFile[] list = target.listFiles();
                int n = list == null ? 0 : list.length;
                return result(true, "Connected to " + host + ". Listed " + n + " share(s) at the host root.");
            }
            if (!target.exists()) {
                return result(false, "Share/path does not exist or is not visible: " + url);
            }
            if (target.isDirectory()) {
                SmbFile[] list = target.listFiles();
                int n = list == null ? 0 : list.length;
                return result(true, "Connected and opened folder. Found " + n + " item(s) in this folder.");
            }
            return result(true, "Connected and opened file resource.");
        } catch (SmbException e) {
            Throwable root = rootCause(e);
            if (root instanceof UnknownHostException) {
                return result(false, "Host not reachable: " + host);
            }
            return result(false, "SMB error: " + summarize(e));
        } catch (Exception e) {
            return result(false, "SMB error: " + summarize(e));
        }
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

    private static JSONObject result(boolean ok, String message) throws JSONException {
        return new JSONObject().put("ok", ok).put("message", message);
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

    private static Throwable rootCause(Throwable e) {
        Throwable c = e;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c;
    }
}
