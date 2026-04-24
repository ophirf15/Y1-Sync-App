package io.innoasis.y1syncer.sync.protocol;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.innoasis.y1syncer.models.SyncProfile;

public class SmbRemoteClient implements RemoteClient {
    @Override
    public List<RemoteFileEntry> listFiles(SyncProfile profile) {
        // Stage 1: SMB listing is scaffolded; this returns empty until profile-specific auth wiring is completed.
        return new ArrayList<RemoteFileEntry>();
    }

    @Override
    public void downloadToFile(SyncProfile profile, String remotePath, File localPartFile) throws IOException {
        // Stage 1: placeholder for jcifs-ng stream copy.
        if (!localPartFile.exists() && !localPartFile.createNewFile()) {
            throw new IOException("Failed to create temp file");
        }
    }
}
