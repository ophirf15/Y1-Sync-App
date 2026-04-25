package io.innoasis.y1syncer.storage;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class StorageBrowser {
    private final Context context;

    public StorageBrowser(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Resolves the on-device folder where sync writes files (creates directories as needed).
     *
     * @param localRootType {@code INTERNAL} or {@code SDCARD} (case-insensitive)
     * @param localDestination relative path under that root, e.g. {@code Music}
     */
    public File resolveSyncDestinationDirectory(String localRootType, String localDestination) throws IOException {
        String type = localRootType == null ? "INTERNAL" : localRootType.trim().toUpperCase();
        String rel = localDestination == null || localDestination.trim().length() == 0
                ? "Music"
                : localDestination.trim().replace('\\', File.separatorChar);
        File baseRoot;
        if ("SDCARD".equals(type)) {
            File removable = firstRemovableStorageRoot();
            baseRoot = removable != null ? removable : Environment.getExternalStorageDirectory();
        } else {
            baseRoot = Environment.getExternalStorageDirectory();
        }
        if (baseRoot == null) {
            throw new IOException("No storage root available");
        }
        File dest = new File(baseRoot, rel);
        if (!dest.exists() && !dest.mkdirs()) {
            throw new IOException("Cannot create directory: " + dest.getAbsolutePath());
        }
        if (!dest.isDirectory()) {
            throw new IOException("Sync destination is not a directory: " + dest.getAbsolutePath());
        }
        return dest;
    }

    /**
     * First removable volume root (same logic as {@link #listRoots} SDCARD entries), or null.
     */
    public File firstRemovableStorageRoot() {
        File internal = Environment.getExternalStorageDirectory();
        if (Build.VERSION.SDK_INT >= 19) {
            File[] extDirs = context.getExternalFilesDirs(null);
            if (extDirs != null) {
                for (int i = 0; i < extDirs.length; i++) {
                    File dir = extDirs[i];
                    if (dir == null) {
                        continue;
                    }
                    File root = trimToStorageRoot(dir);
                    if (root != null && !samePath(root, internal)) {
                        return root;
                    }
                }
            }
        } else {
            File ext = context.getExternalFilesDir(null);
            if (ext != null) {
                File root = trimToStorageRoot(ext);
                if (root != null && !samePath(root, internal)) {
                    return root;
                }
            }
        }
        return null;
    }

    public JSONArray listRoots() throws JSONException {
        JSONArray roots = new JSONArray();

        File internal = Environment.getExternalStorageDirectory();
        if (internal != null) {
            try {
                roots.put(rootToJson("INTERNAL", internal, false));
            } catch (IllegalArgumentException | SecurityException ignored) {
            }
        }

        if (Build.VERSION.SDK_INT >= 19) {
            File[] extDirs = context.getExternalFilesDirs(null);
            if (extDirs != null) {
                for (int i = 0; i < extDirs.length; i++) {
                    File dir = extDirs[i];
                    if (dir == null) {
                        continue;
                    }
                    File root = trimToStorageRoot(dir);
                    if (root == null || samePath(root, internal)) {
                        continue;
                    }
                    try {
                        roots.put(rootToJson("SDCARD", root, true));
                    } catch (IllegalArgumentException | SecurityException ignored) {
                    }
                }
            }
        } else {
            File ext = context.getExternalFilesDir(null);
            if (ext != null) {
                File root = trimToStorageRoot(ext);
                if (root != null && !samePath(root, internal)) {
                    try {
                        roots.put(rootToJson("SDCARD", root, true));
                    } catch (IllegalArgumentException | SecurityException ignored) {
                    }
                }
            }
        }
        return roots;
    }

    public JSONArray listChildren(Map<String, String> query) throws JSONException {
        String rootPath = query.get("root");
        String path = query.get("path");
        File root = rootPath == null || rootPath.length() == 0 ? Environment.getExternalStorageDirectory() : new File(rootPath);
        File current = (path == null || path.length() == 0) ? root : new File(root, path);
        JSONArray out = new JSONArray();
        if (current == null || !current.exists() || !current.isDirectory()) {
            return out;
        }
        File[] children = current.listFiles();
        if (children == null) {
            return out;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                JSONObject row = new JSONObject();
                row.put("name", child.getName());
                row.put("relative_path", relativize(root, child));
                row.put("writable", child.canWrite());
                out.put(row);
            }
        }
        return out;
    }

    private JSONObject rootToJson(String type, File root, boolean removable) throws JSONException {
        StatFs statFs = new StatFs(root.getAbsolutePath());
        long total;
        long free;
        if (Build.VERSION.SDK_INT >= 18) {
            total = statFs.getTotalBytes();
            free = statFs.getAvailableBytes();
        } else {
            total = (long) statFs.getBlockCount() * (long) statFs.getBlockSize();
            free = (long) statFs.getAvailableBlocks() * (long) statFs.getBlockSize();
        }
        return new JSONObject()
                .put("type", type)
                .put("path", root.getAbsolutePath())
                .put("removable", removable)
                .put("writable", root.canWrite())
                .put("free_bytes", free)
                .put("total_bytes", total);
    }

    private File trimToStorageRoot(File dir) {
        File current = dir;
        while (current != null && current.getParentFile() != null && current.getParentFile().getParentFile() != null) {
            String name = current.getName();
            if ("Android".equals(name)) {
                return current.getParentFile();
            }
            current = current.getParentFile();
        }
        return dir;
    }

    private boolean samePath(File a, File b) {
        if (a == null || b == null) {
            return false;
        }
        return a.getAbsolutePath().equals(b.getAbsolutePath());
    }

    private String relativize(File root, File file) {
        String rootPath = root.getAbsolutePath();
        String path = file.getAbsolutePath();
        if (path.startsWith(rootPath)) {
            String rel = path.substring(rootPath.length());
            return rel.startsWith(File.separator) ? rel.substring(1) : rel;
        }
        return file.getName();
    }
}
