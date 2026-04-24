package io.innoasis.y1syncer.server;

public class ApiResponse {
    public final int status;
    public final String body;

    public ApiResponse(int status, String body) {
        this.status = status;
        this.body = body;
    }
}
