package io.innoasis.y1syncer.sync;

public interface SyncProgressListener {
    void onSyncStart(long profileId, String profileName, int totalFiles, long totalBytes);
    void onFileStart(String remotePath, int index, int total, long fileSize);
    void onFileResult(String remotePath, boolean downloaded, boolean skipped, String errorMessage, long cumulativeBytesDone);
    void onSyncDone(int attempted, int downloaded, int skipped, int failed, String summary, String errorMessage);
}
