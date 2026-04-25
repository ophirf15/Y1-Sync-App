package io.innoasis.y1syncer.server;

public class ApiResponse {
    public final int status;
    public final String body;
    /** When null, server uses {@code application/json}. */
    public final String contentType;

    public ApiResponse(int status, String body) {
        this(status, body, null);
    }

    public ApiResponse(int status, String body, String contentType) {
        this.status = status;
        this.body = body;
        this.contentType = contentType;
    }
}
