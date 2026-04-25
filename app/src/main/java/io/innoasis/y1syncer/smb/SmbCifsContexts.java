package io.innoasis.y1syncer.smb;

import java.util.Properties;

import jcifs.CIFSContext;
import jcifs.CIFSException;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;

/**
 * Builds an isolated jcifs-ng {@link CIFSContext} per request. Avoids {@link jcifs.context.SingletonContext},
 * which can fail on Android, and tunes defaults for typical home NAS / Windows shares.
 */
public final class SmbCifsContexts {

    private SmbCifsContexts() {
    }

    public static CIFSContext forUser(String domain, String user, String password) throws CIFSException {
        Properties p = new Properties();
        // Default true breaks many SMB3-without-secure-negotiate home servers (500 / connection reset).
        p.setProperty("jcifs.smb.client.requireSecureNegotiate", "false");
        // Android JREs omit Cp850; stock jcifs initDefaults may still reference it from compiled bytecode.
        p.setProperty("jcifs.encoding", "ISO-8859-1");
        AndroidJcifsPropertyConfiguration cfg = new AndroidJcifsPropertyConfiguration(p);
        return new BaseContext(cfg).withCredentials(new NtlmPasswordAuthenticator(domain, user, password));
    }
}
