package io.innoasis.y1syncer.updates;

import android.content.Context;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import io.innoasis.y1syncer.R;

final class UpdateHttp {
    private static final String[] GITHUB_HOST_SUFFIXES = new String[]{
            "github.com",
            "githubusercontent.com"
    };

    private UpdateHttp() {
    }

    static HttpURLConnection open(Context context, String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        TlsCompat.configure(conn);
        if (conn instanceof HttpsURLConnection && isGithubHost(conn.getURL().getHost())) {
            applyBundledTrust((HttpsURLConnection) conn, context);
        }
        return conn;
    }

    private static boolean isGithubHost(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.US);
        for (String suffix : GITHUB_HOST_SUFFIXES) {
            if (h.equals(suffix) || h.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    private static void applyBundledTrust(HttpsURLConnection conn, Context context) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate cert;
        InputStream in = context.getResources().openRawResource(R.raw.le_r12);
        try {
            cert = cf.generateCertificate(in);
        } finally {
            in.close();
        }

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setCertificateEntry("le-r12", cert);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);

        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, tmf.getTrustManagers(), null);
        SSLSocketFactory sf = ssl.getSocketFactory();
        if (android.os.Build.VERSION.SDK_INT < 21) {
            sf = new Tls12OnlySocketFactory(sf);
        }
        conn.setSSLSocketFactory(sf);
    }

    private static final class Tls12OnlySocketFactory extends SSLSocketFactory {
        private static final String[] TLS12_ONLY = new String[]{"TLSv1.2"};
        private final SSLSocketFactory delegate;

        private Tls12OnlySocketFactory(SSLSocketFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws java.io.IOException {
            return patch(delegate.createSocket(s, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws java.io.IOException {
            return patch(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws java.io.IOException {
            return patch(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws java.io.IOException {
            return patch(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws java.io.IOException {
            return patch(delegate.createSocket(address, port, localAddress, localPort));
        }

        private Socket patch(Socket socket) {
            if (socket instanceof SSLSocket) {
                ((SSLSocket) socket).setEnabledProtocols(TLS12_ONLY);
            }
            return socket;
        }
    }
}
