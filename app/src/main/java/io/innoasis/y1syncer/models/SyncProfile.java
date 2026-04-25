package io.innoasis.y1syncer.models;

/**
 * Runtime snapshot of a sync profile for SMB (and future protocols).
 */
public class SyncProfile {
    public long id;
    public String name;
    public String protocol;
    public String host;
    public int port;
    public String shareName;
    public String username;
    public String password;
    public String domain;
    public String remoteRootPath;
    public String localDestination;
    public String localRootType;
    public boolean active;
    public boolean includeSubfolders;
    /** Comma-separated extensions, e.g. {@code mp3,flac}; empty means all files. */
    public String allowedTypesCsv;
    /** Temp suffix while downloading, e.g. {@code .part}. */
    public String tempExtension;
}
