package io.innoasis.y1syncer.runtime;

public class SyncStatusState {
    public String state = "idle"; // idle|running|error|done
    public long profileId;
    public String profileName = "";
    public String triggerType = "";
    public String currentFile = "";
    public int currentIndex;
    public int totalFiles;
    public long bytesDone;
    public long bytesTotal;
    public long startedAt;
    public long updatedAt;
    public int downloadedFiles;
    public int skippedFiles;
    public int failedFiles;
    public String lastError = "";
    public String summary = "";
}
