package io.innoasis.y1syncer.server;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.IOException;
import java.io.InputStream;

import io.innoasis.y1syncer.updates.BundleStorage;

public class AssetResolver {
    private final AssetManager assetManager;
    private final BundleStorage bundleStorage;

    public AssetResolver(Context context, BundleStorage bundleStorage) {
        this.assetManager = context.getAssets();
        this.bundleStorage = bundleStorage;
    }

    public InputStream openAsset(String path) throws IOException {
        InputStream stream = bundleStorage.openActiveAsset(path);
        if (stream != null) {
            return stream;
        }
        String normalized = "web/bundled/" + (path.startsWith("/") ? path.substring(1) : path);
        if (normalized.endsWith("/")) {
            normalized += "index.html";
        }
        return assetManager.open(normalized);
    }
}
