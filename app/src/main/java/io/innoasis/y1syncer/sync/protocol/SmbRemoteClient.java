package io.innoasis.y1syncer.sync.protocol;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.innoasis.y1syncer.models.SyncProfile;
import io.innoasis.y1syncer.smb.SmbCifsContexts;
import io.innoasis.y1syncer.smb.SmbUrlPaths;

import jcifs.CIFSContext;
import jcifs.smb.SmbFile;

public class SmbRemoteClient implements RemoteClient {

    @Override
    public List<RemoteFileEntry> listFiles(SyncProfile profile) throws IOException {
        validate(profile);
        CIFSContext ctx = newContext(profile);
        String rootUrl = buildRootUrl(profile);
        List<RemoteFileEntry> out = new ArrayList<RemoteFileEntry>();
        try {
            SmbFile root = new SmbFile(rootUrl, ctx);
            if (!root.exists()) {
                throw new IOException("Remote root does not exist: " + rootUrl);
            }
            if (!root.isDirectory()) {
                throw new IOException("Remote root is not a directory: " + rootUrl);
            }
            walk(root, "", profile.includeSubfolders, allowedExtSet(profile.allowedTypesCsv), out);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(summarize(e), e);
        }
        return out;
    }

    @Override
    public void downloadToFile(SyncProfile profile, String remotePath, File localPartFile) throws IOException {
        validate(profile);
        if (remotePath == null || remotePath.trim().isEmpty()) {
            throw new IOException("remote path is empty");
        }
        File parent = localPartFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create parent dir: " + parent);
        }
        CIFSContext ctx = newContext(profile);
        String url = buildFileUrl(profile, remotePath);
        InputStream in = null;
        OutputStream out = null;
        try {
            SmbFile src = new SmbFile(url, ctx);
            if (src.isDirectory()) {
                throw new IOException("Remote path is a directory: " + remotePath);
            }
            in = new BufferedInputStream(src.getInputStream());
            out = new BufferedOutputStream(new FileOutputStream(localPartFile));
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } catch (IOException e) {
            if (localPartFile.exists() && !localPartFile.delete()) {
                // ignore cleanup failure
            }
            throw e;
        } catch (Exception e) {
            if (localPartFile.exists() && !localPartFile.delete()) {
                // ignore
            }
            throw new IOException(summarize(e), e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static CIFSContext newContext(SyncProfile profile) throws IOException {
        try {
            return SmbCifsContexts.forUser(
                    profile.domain == null ? "" : profile.domain,
                    profile.username == null ? "" : profile.username,
                    profile.password == null ? "" : profile.password);
        } catch (Exception e) {
            throw new IOException("SMB client init failed: " + summarize(e), e);
        }
    }

    private static void validate(SyncProfile p) throws IOException {
        if (p == null) {
            throw new IOException("profile is null");
        }
        if (p.host == null || p.host.trim().isEmpty()) {
            throw new IOException("host is required");
        }
        if (p.shareName == null || p.shareName.trim().isEmpty()) {
            throw new IOException("share_name is required");
        }
        if (p.password == null || p.password.isEmpty()) {
            throw new IOException("password is required");
        }
    }

    private static String buildRootUrl(SyncProfile p) throws IOException {
        String host = p.host.trim();
        String share = p.shareName.trim();
        int port = p.port > 0 ? p.port : 445;
        StringBuilder sb = new StringBuilder();
        sb.append("smb://").append(host).append(":").append(port).append("/");
        sb.append(SmbUrlPaths.validateSegment(share)).append("/");
        sb.append(SmbUrlPaths.pathSuffix(p.remoteRootPath));
        String url = sb.toString();
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        return url;
    }

    private static String buildFileUrl(SyncProfile p, String remoteRelPath) throws IOException {
        String base = buildRootUrl(p);
        String norm = remoteRelPath.trim().replace('\\', '/');
        while (norm.startsWith("/")) {
            norm = norm.substring(1);
        }
        if (norm.isEmpty()) {
            throw new IOException("empty remote path");
        }
        StringBuilder rel = new StringBuilder();
        for (String seg : norm.split("/")) {
            if (seg.isEmpty()) {
                continue;
            }
            rel.append(SmbUrlPaths.validateSegment(seg)).append("/");
        }
        if (rel.length() > 0 && rel.charAt(rel.length() - 1) == '/') {
            rel.setLength(rel.length() - 1);
        }
        return base + rel.toString();
    }

    private static void walk(SmbFile dir, String relPrefix, boolean recurse, Set<String> allowedExt, List<RemoteFileEntry> out) throws Exception {
        SmbFile[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (SmbFile child : children) {
            String name = stripTrailingSlash(child.getName());
            if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
                continue;
            }
            if (isAdminShare(name)) {
                continue;
            }
            String rel = relPrefix.isEmpty() ? name : relPrefix + "/" + name;
            if (child.isDirectory()) {
                if (recurse) {
                    walk(child, rel, true, allowedExt, out);
                }
            } else {
                if (!extensionAllowed(name, allowedExt)) {
                    continue;
                }
                RemoteFileEntry e = new RemoteFileEntry();
                e.remotePath = rel.replace('\\', '/');
                e.size = child.length();
                e.modifiedTs = child.getLastModified();
                out.add(e);
            }
        }
    }

    private static Set<String> allowedExtSet(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return null;
        }
        Set<String> s = new HashSet<String>();
        for (String part : csv.split("[,;]")) {
            String t = part.trim().toLowerCase();
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith(".")) {
                t = t.substring(1);
            }
            s.add(t);
        }
        return s.isEmpty() ? null : s;
    }

    private static boolean extensionAllowed(String filename, Set<String> allowed) {
        if (allowed == null) {
            return true;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot >= filename.length() - 1) {
            return false;
        }
        String ext = filename.substring(dot + 1).toLowerCase();
        return allowed.contains(ext);
    }

    private static boolean isAdminShare(String name) {
        String u = name.toUpperCase();
        return "IPC$".equals(u) || "ADMIN$".equals(u);
    }

    private static String stripTrailingSlash(String s) {
        if (s != null && s.endsWith("/")) {
            return s.substring(0, s.length() - 1);
        }
        return s == null ? "" : s;
    }

    private static String summarize(Throwable e) {
        String m = e.getMessage();
        if (m != null && m.trim().length() > 0) {
            return m;
        }
        Throwable c = e.getCause();
        if (c != null && c.getMessage() != null && c.getMessage().trim().length() > 0) {
            return c.getMessage();
        }
        return e.getClass().getSimpleName();
    }
}
