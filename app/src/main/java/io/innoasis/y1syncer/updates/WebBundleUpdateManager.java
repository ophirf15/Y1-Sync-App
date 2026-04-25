package io.innoasis.y1syncer.updates;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.innoasis.y1syncer.db.repos.LogRepository;
import io.innoasis.y1syncer.db.repos.UpdateBundleRepository;

public class WebBundleUpdateManager {
    private static final String TAG = "WebBundleUpdateManager";

    private final BundleStorage bundleStorage;
    private final UpdateBundleRepository updateBundleRepository;
    private final LogRepository logRepository;

    public WebBundleUpdateManager(
            BundleStorage bundleStorage,
            UpdateBundleRepository updateBundleRepository,
            LogRepository logRepository
    ) {
        this.bundleStorage = bundleStorage;
        this.updateBundleRepository = updateBundleRepository;
        this.logRepository = logRepository;
    }

    public JSONObject checkForUpdate(String manifestUrl) throws JSONException {
        try {
            JSONObject manifest = fetchJson(manifestUrl);
            JSONObject active = updateBundleRepository.getActiveBundle();
            String activeVersion = active == null ? "bundled" : active.optString("resource_version", "bundled");
            String remoteVersion = manifest.optString("resource_version", "unknown");
            boolean updateAvailable = !remoteVersion.equals(activeVersion);
            JSONObject json = new JSONObject();
            json.put("manifest_url", manifestUrl);
            json.put("active_version", activeVersion);
            json.put("remote_version", remoteVersion);
            json.put("update_available", updateAvailable);
            json.put("bundle_url", manifest.optString("bundle_url", ""));
            json.put("checksum_sha256", manifest.optString("checksum_sha256", ""));
            logRepository.addLog("INFO", "Checked updates from " + manifestUrl + ", available=" + updateAvailable);
            return json;
        } catch (Exception e) {
            logRepository.addLog("ERROR", "Update check failed: " + e.getMessage());
            return new JSONObject().put("manifest_url", manifestUrl).put("error", e.getMessage());
        }
    }

    public void markInstalledBundle(String version, File folder, String checksum, String manifestUrl) {
        updateBundleRepository.setActiveBundle(version, folder.getAbsolutePath(), checksum, manifestUrl, "ACTIVE");
        logRepository.addLog("INFO", "Activated web bundle " + version);
    }

    public JSONObject downloadAndApply(String manifestUrl) throws JSONException {
        try {
            JSONObject manifest = fetchJson(manifestUrl);
            String version = manifest.getString("resource_version");
            String bundleUrl = manifest.getString("bundle_url");
            String checksum = manifest.optString("checksum_sha256", "");

            File root = getBundleRoot();
            File zipFile = new File(root, "bundle-" + version + ".zip");
            downloadToFile(bundleUrl, zipFile);

            if (checksum.length() > 0) {
                String actual = sha256(zipFile);
                if (!checksum.equalsIgnoreCase(actual)) {
                    throw new IllegalStateException("Checksum mismatch");
                }
            }

            File outDir = new File(root, "v-" + version);
            if (!outDir.exists()) {
                outDir.mkdirs();
            }
            unzip(zipFile, outDir);
            markInstalledBundle(version, outDir, checksum, manifestUrl);
            return new JSONObject().put("applied", true).put("version", version).put("path", outDir.getAbsolutePath());
        } catch (Exception e) {
            logRepository.addLog("ERROR", "Apply update failed: " + e.getMessage());
            return new JSONObject().put("applied", false).put("error", e.getMessage());
        }
    }

    public void revertToBundled() {
        updateBundleRepository.setActiveBundle("bundled", "", "", "", "BUNDLED");
        logRepository.addLog("INFO", "Reverted to bundled web assets");
        Log.i(TAG, "Reverted to bundled assets");
    }

    public File getBundleRoot() {
        return bundleStorage.getBundlesRoot();
    }

    private JSONObject fetchJson(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestMethod("GET");
        InputStream in = conn.getInputStream();
        try {
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, "UTF-8"));
            }
            return new JSONObject(sb.toString());
        } finally {
            in.close();
            conn.disconnect();
        }
    }

    private void downloadToFile(String url, File target) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestMethod("GET");
        InputStream in = new BufferedInputStream(conn.getInputStream());
        FileOutputStream out = new FileOutputStream(target);
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } finally {
            out.close();
            in.close();
            conn.disconnect();
        }
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        FileInputStream in = new FileInputStream(file);
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                digest.update(buf, 0, n);
            }
        } finally {
            in.close();
        }
        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void unzip(File zip, File targetDir) throws Exception {
        ZipInputStream zin = new ZipInputStream(new FileInputStream(zip));
        try {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zin.getNextEntry()) != null) {
                File out = new File(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                FileOutputStream fout = new FileOutputStream(out);
                try {
                    int n;
                    while ((n = zin.read(buffer)) > 0) {
                        fout.write(buffer, 0, n);
                    }
                } finally {
                    fout.close();
                }
            }
        } finally {
            zin.close();
        }
    }
}
