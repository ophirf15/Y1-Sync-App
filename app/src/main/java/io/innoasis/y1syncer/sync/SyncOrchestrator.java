package io.innoasis.y1syncer.sync;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.List;

import io.innoasis.y1syncer.db.repos.LogRepository;
import io.innoasis.y1syncer.db.repos.ProfileRepository;
import io.innoasis.y1syncer.db.repos.SyncStateRepository;
import io.innoasis.y1syncer.library.LibraryIndexer;
import io.innoasis.y1syncer.models.SyncProfile;
import io.innoasis.y1syncer.storage.StorageBrowser;
import io.innoasis.y1syncer.sync.protocol.RemoteClient;
import io.innoasis.y1syncer.sync.protocol.RemoteFileEntry;
import io.innoasis.y1syncer.sync.protocol.SmbRemoteClient;
import io.innoasis.y1syncer.util.MediaScanHelper;

public class SyncOrchestrator {
    private static final String TAG = "SyncOrchestrator";
    private final Context appContext;
    private final LogRepository logRepository;
    private final ProfileRepository profileRepository;
    private final StorageBrowser storageBrowser;
    private final LibraryIndexer libraryIndexer;
    private final SyncStateRepository syncStateRepository;
    private final RemoteClient smbClient;
    private final SyncProgressListener progressListener;

    public SyncOrchestrator(Context appContext, LogRepository logRepository, ProfileRepository profileRepository, StorageBrowser storageBrowser, LibraryIndexer libraryIndexer, SyncStateRepository syncStateRepository, SyncProgressListener progressListener) {
        this.appContext = appContext.getApplicationContext();
        this.logRepository = logRepository;
        this.profileRepository = profileRepository;
        this.storageBrowser = storageBrowser;
        this.libraryIndexer = libraryIndexer;
        this.syncStateRepository = syncStateRepository;
        this.smbClient = new SmbRemoteClient();
        this.progressListener = progressListener;
    }

    /**
     * Runs sync for an explicit profile snapshot (e.g. tests). When {@code null}, picks the first SMB profile.
     *
     * @return short human-readable result for status UI
     */
    public String syncNow(SyncProfile profile) {
        try {
            if (profile != null) {
                return runSmbCopy(profile);
            }
            long id = profileRepository.pickProfileIdForTriggerSync();
            if (id <= 0) {
                logRepository.addLog("WARN", "No SMB profile configured for sync");
                return "no SMB profile";
            }
            return syncProfileById(id);
        } catch (Exception e) {
            Log.e(TAG, "sync failed", e);
            logRepository.addLog("ERROR", "Sync failed: " + e.getMessage());
            return e.getMessage();
        }
    }

    public String syncProfileById(long id) {
        try {
            SyncProfile p = profileRepository.loadProfileForSync(id);
            if (p == null) {
                logRepository.addLog("WARN", "syncProfileById: profile not found id=" + id);
                return "not found";
            }
            return runSmbCopy(p);
        } catch (Exception e) {
            Log.e(TAG, "load profile for sync failed", e);
            logRepository.addLog("ERROR", "Sync failed: " + e.getMessage());
            return e.getMessage();
        }
    }

    private String runSmbCopy(SyncProfile p) throws IOException {
        if (!"SMB".equalsIgnoreCase(p.protocol)) {
            logRepository.addLog("WARN", "Sync skipped: protocol " + p.protocol + " (SMB only in v1)");
            return "skipped (non-SMB)";
        }
        logRepository.addLog("INFO", "Sync start profile=" + p.name + " (id=" + p.id + ")");
        File destRoot = storageBrowser.resolveSyncDestinationDirectory(p.localRootType, p.localDestination);
        List<RemoteFileEntry> files = smbClient.listFiles(p);
        long totalBytes = 0L;
        for (RemoteFileEntry f : files) {
            totalBytes += Math.max(0L, f.size);
        }
        if (progressListener != null) {
            progressListener.onSyncStart(p.id, p.name, files.size(), totalBytes);
        }
        int ok = 0;
        int skipped = 0;
        int fail = 0;
        long bytesDone = 0L;
        String tempExt = p.tempExtension == null ? ".part" : p.tempExtension;
        for (int i = 0; i < files.size(); i++) {
            RemoteFileEntry entry = files.get(i);
            if (progressListener != null) {
                progressListener.onFileStart(entry.remotePath, i + 1, files.size(), entry.size);
            }
            String rel = entry.remotePath.replace('/', File.separatorChar);
            File out = new File(destRoot, rel);
            File parent = out.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                logRepository.addLog("ERROR", "mkdirs failed: " + parent.getAbsolutePath());
                syncStateRepository.upsertFileState(p.id, p.protocol, entry.remotePath, out.getAbsolutePath(), entry.size, entry.modifiedTs,
                        "mkdirs failed", "failed");
                fail++;
                if (progressListener != null) {
                    progressListener.onFileResult(entry.remotePath, false, false, "mkdirs failed", bytesDone);
                }
                continue;
            }
            if (out.exists() && out.isFile() && out.length() == entry.size && out.lastModified() == entry.modifiedTs) {
                syncStateRepository.upsertFileState(p.id, p.protocol, entry.remotePath, out.getAbsolutePath(), entry.size, entry.modifiedTs,
                        "unchanged", "skipped");
                skipped++;
                bytesDone += Math.max(0L, entry.size);
                if (progressListener != null) {
                    progressListener.onFileResult(entry.remotePath, false, true, null, bytesDone);
                }
                continue;
            }
            File part = new File(out.getParentFile(), out.getName() + tempExt);
            try {
                smbClient.downloadToFile(p, entry.remotePath, part);
                if (out.exists() && !out.delete()) {
                    throw new IOException("Cannot replace existing file: " + out.getAbsolutePath());
                }
                if (!part.renameTo(out)) {
                    throw new IOException("Rename failed: " + part.getAbsolutePath());
                }
                libraryIndexer.indexFile(p.id, out);
                MediaScanHelper.scanFile(appContext, out);
                if (entry.modifiedTs > 0) {
                    // align local metadata for future incremental checks
                    out.setLastModified(entry.modifiedTs);
                }
                syncStateRepository.upsertFileState(p.id, p.protocol, entry.remotePath, out.getAbsolutePath(), entry.size, entry.modifiedTs,
                        "ok", "downloaded");
                bytesDone += Math.max(0L, entry.size);
                ok++;
                if (progressListener != null) {
                    progressListener.onFileResult(entry.remotePath, true, false, null, bytesDone);
                }
            } catch (IOException e) {
                if (part.exists()) {
                    part.delete();
                }
                logRepository.addLog("ERROR", "Download failed " + entry.remotePath + ": " + e.getMessage());
                syncStateRepository.upsertFileState(p.id, p.protocol, entry.remotePath, out.getAbsolutePath(), entry.size, entry.modifiedTs,
                        e.getMessage(), "failed");
                fail++;
                if (progressListener != null) {
                    progressListener.onFileResult(entry.remotePath, false, false, e.getMessage(), bytesDone);
                }
            }
        }
        String summary = "files=" + files.size() + " ok=" + ok + " skipped=" + skipped + " fail=" + fail + " -> " + destRoot.getAbsolutePath();
        logRepository.addLog("INFO", "Sync done profile=" + p.name + " " + summary);
        if (progressListener != null) {
            progressListener.onSyncDone(files.size(), ok, skipped, fail, summary, fail > 0 ? "Some files failed" : null);
        }
        return summary;
    }

    public RemoteClient getSmbClient() {
        return smbClient;
    }
}
