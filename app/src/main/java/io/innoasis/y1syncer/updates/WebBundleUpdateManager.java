package io.innoasis.y1syncer.updates;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

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
        JSONObject json = new JSONObject();
        json.put("manifest_url", manifestUrl);
        json.put("status", "Stage 1 check scaffolded");
        json.put("download_supported", true);
        logRepository.addLog("INFO", "Checked updates from " + manifestUrl);
        return json;
    }

    public void markInstalledBundle(String version, File folder, String checksum, String manifestUrl) {
        updateBundleRepository.setActiveBundle(version, folder.getAbsolutePath(), checksum, manifestUrl, "ACTIVE");
        logRepository.addLog("INFO", "Activated web bundle " + version);
    }

    public void revertToBundled() {
        updateBundleRepository.setActiveBundle("bundled", "", "", "", "BUNDLED");
        logRepository.addLog("INFO", "Reverted to bundled web assets");
        Log.i(TAG, "Reverted to bundled assets");
    }

    public File getBundleRoot() {
        return bundleStorage.getBundlesRoot();
    }
}
