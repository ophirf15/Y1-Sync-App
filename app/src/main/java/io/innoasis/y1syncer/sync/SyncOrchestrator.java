package io.innoasis.y1syncer.sync;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;

import io.innoasis.y1syncer.db.repos.LogRepository;
import io.innoasis.y1syncer.models.SyncProfile;
import io.innoasis.y1syncer.sync.protocol.RemoteClient;
import io.innoasis.y1syncer.sync.protocol.SmbRemoteClient;

public class SyncOrchestrator {
    private static final String TAG = "SyncOrchestrator";
    private final Context context;
    private final LogRepository logRepository;
    private final RemoteClient smbClient;

    public SyncOrchestrator(Context context, LogRepository logRepository) {
        this.context = context;
        this.logRepository = logRepository;
        this.smbClient = new SmbRemoteClient();
    }

    public void syncNow(SyncProfile profile) {
        try {
            File base = Environment.getExternalStorageDirectory();
            File targetRoot = new File(base, profile != null ? profile.localDestination : "Music");
            if (!targetRoot.exists()) {
                targetRoot.mkdirs();
            }
            logRepository.addLog("INFO", "Sync start for profile " + (profile != null ? profile.name : "default"));
            // Stage 1 placeholder flow for SMB sync.
            logRepository.addLog("INFO", "Sync completed (skeleton)");
        } catch (Exception e) {
            Log.e(TAG, "sync failed", e);
            logRepository.addLog("ERROR", "Sync failed: " + e.getMessage());
        }
    }

    public RemoteClient getSmbClient() {
        return smbClient;
    }
}
