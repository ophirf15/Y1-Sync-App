package io.innoasis.y1syncer.sync.protocol;

import java.io.File;
import java.io.IOException;
import java.util.List;

import io.innoasis.y1syncer.models.SyncProfile;

public interface RemoteClient {
    List<RemoteFileEntry> listFiles(SyncProfile profile) throws IOException;
    void downloadToFile(SyncProfile profile, String remotePath, File localPartFile) throws IOException;
}
