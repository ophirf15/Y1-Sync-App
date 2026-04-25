package io.innoasis.y1syncer.server;

import android.util.Log;

import fi.iki.elonen.NanoHTTPD;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

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
            try {
                ApiResponse response = apiRouter.handle(session.getMethod().name(), uri, session.getParms(), extractBody(session));
                return newFixedLengthResponse(Response.Status.lookup(response.status), "application/json", response.body);
            } catch (Throwable t) {
                Log.e(TAG, "API error: " + uri, t);
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                        "{\"error\":\"internal\"}");
            }
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

    /**
     * NanoHTTPD stores JSON POST bodies in {@code postData}. For {@code PUT}, the raw body is written
     * to a temp file and the path is stored under {@code content} — reading only {@code postData}
     * leaves profile updates with an empty body and effectively wipes fields on save.
     */
    private String extractBody(IHTTPSession session) {
        Map<String, String> files = new HashMap<String, String>();
        try {
            session.parseBody(files);
            String postData = files.get("postData");
            if (postData != null && postData.length() > 0) {
                return postData;
            }
            String tmpPath = files.get("content");
            if (tmpPath == null || tmpPath.length() == 0) {
                return "";
            }
            File f = new File(tmpPath);
            if (!f.isFile()) {
                return "";
            }
            FileInputStream in = null;
            try {
                in = new FileInputStream(f);
                return streamToString(in);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                    }
                }
                if (!f.delete()) {
                    Log.w(TAG, "Could not delete temp PUT body: " + tmpPath);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "extractBody failed", e);
            return "";
        }
    }
}
