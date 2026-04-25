package io.innoasis.y1syncer.sync.protocol;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.innoasis.y1syncer.models.SyncProfile;

public class WebDavRemoteClientStub implements RemoteClient {
    @Override
    public List<RemoteFileEntry> listFiles(SyncProfile profile) throws IOException {
        return new ArrayList<RemoteFileEntry>();
    }

    @Override
    public void downloadToFile(SyncProfile profile, String remotePath, File localPartFile) throws IOException {
        throw new IOException("WebDAV not implemented in Stage 1");
    }
}
