package io.innoasis.y1syncer.smb;

/**
 * Normalizes SMB URL path segments for jcifs {@code smb://} URLs.
 */
public final class SmbUrlPaths {

    private SmbUrlPaths() {
    }

    public static String validateSegment(String seg) {
        if (seg == null || seg.contains("/") || seg.contains("\\")) {
            throw new IllegalArgumentException("invalid path segment");
        }
        return seg;
    }

    /**
     * Turns a remote root like {@code /Album/2020} into {@code Album/2020/}; {@code /} or empty becomes "".
     */
    public static String pathSuffix(String remoteRoot) {
        String p = remoteRoot == null ? "" : remoteRoot.trim().replace('\\', '/');
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/") && p.length() > 1) {
            p = p.substring(0, p.length() - 1);
        }
        if (p.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String seg : p.split("/")) {
            if (seg.isEmpty()) {
                continue;
            }
            sb.append(validateSegment(seg)).append("/");
        }
        return sb.toString();
    }
}
