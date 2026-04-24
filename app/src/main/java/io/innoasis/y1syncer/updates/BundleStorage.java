package io.innoasis.y1syncer.updates;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import io.innoasis.y1syncer.db.repos.UpdateBundleRepository;

public class BundleStorage {
    private final Context context;
    private final UpdateBundleRepository updateBundleRepository;

    public BundleStorage(Context context, UpdateBundleRepository updateBundleRepository) {
        this.context = context;
        this.updateBundleRepository = updateBundleRepository;
    }

    public File getBundlesRoot() {
        File root = new File(context.getFilesDir(), "web_bundles");
        if (!root.exists()) {
            root.mkdirs();
        }
        return root;
    }

    public InputStream openActiveAsset(String requestPath) throws IOException {
        String path = updateBundleRepository.getActiveBundlePath();
        if (path == null || path.length() == 0) {
            return null;
        }
        String clean = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;
        File target = new File(path, clean.length() == 0 ? "index.html" : clean);
        if (!target.exists() || target.isDirectory()) {
            return null;
        }
        return new FileInputStream(target);
    }
}
