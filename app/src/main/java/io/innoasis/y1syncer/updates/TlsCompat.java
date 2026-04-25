package io.innoasis.y1syncer.updates;

import android.os.Build;

import java.io.IOException;
import java.net.Socket;
import java.net.URLConnection;
import java.security.GeneralSecurityException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Enables TLS 1.2 for HTTPS connections on older Android versions.
 */
final class TlsCompat {

    private TlsCompat() {
    }

    static void configure(URLConnection conn) {
        if (!(conn instanceof HttpsURLConnection)) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 21) {
            return;
        }
        HttpsURLConnection https = (HttpsURLConnection) conn;
        SSLSocketFactory factory = tls12Factory();
        if (factory != null) {
            https.setSSLSocketFactory(factory);
        }
    }

    private static SSLSocketFactory tls12Factory() {
        try {
            SSLContext context = SSLContext.getInstance("TLSv1.2");
            context.init(null, null, null);
            return new Tls12SocketFactory(context.getSocketFactory());
        } catch (GeneralSecurityException ignored) {
            try {
                SSLContext fallback = SSLContext.getInstance("TLS");
                fallback.init(null, null, null);
                return new Tls12SocketFactory(fallback.getSocketFactory());
            } catch (GeneralSecurityException ignoredToo) {
                return null;
            }
        }
    }

    private static final class Tls12SocketFactory extends SSLSocketFactory {
        private static final String[] TLS12_ONLY = new String[]{"TLSv1.2"};
        private final SSLSocketFactory delegate;

        private Tls12SocketFactory(SSLSocketFactory delegate) {
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
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return patch(delegate.createSocket(s, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return patch(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, java.net.InetAddress localHost, int localPort) throws IOException {
            return patch(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(java.net.InetAddress host, int port) throws IOException {
            return patch(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(java.net.InetAddress address, int port, java.net.InetAddress localAddress, int localPort) throws IOException {
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
