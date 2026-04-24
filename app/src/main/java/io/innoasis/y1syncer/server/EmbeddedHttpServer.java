package io.innoasis.y1syncer.server;

import android.util.Log;

import fi.iki.elonen.NanoHTTPD;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class EmbeddedHttpServer extends NanoHTTPD {
    private static final String TAG = "EmbeddedHttpServer";
    private final ApiRouter apiRouter;
    private final AssetResolver assetResolver;

    public EmbeddedHttpServer(int port, ApiRouter apiRouter, AssetResolver assetResolver) {
        super(port);
        this.apiRouter = apiRouter;
        this.assetResolver = assetResolver;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        if (uri.startsWith("/api/")) {
            ApiResponse response = apiRouter.handle(session.getMethod().name(), uri);
            return newFixedLengthResponse(Response.Status.lookup(response.status), "application/json", response.body);
        }
        try {
            String path = uri.equals("/") ? "/index.html" : uri;
            InputStream stream = assetResolver.openAsset(path);
            String body = streamToString(stream);
            return newFixedLengthResponse(Response.Status.OK, guessMime(path), body);
        } catch (IOException e) {
            Log.w(TAG, "Asset missing: " + uri, e);
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found");
        }
    }

    private String guessMime(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".css")) return "text/css";
        return "text/plain";
    }

    private String streamToString(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toString("UTF-8");
    }
}
