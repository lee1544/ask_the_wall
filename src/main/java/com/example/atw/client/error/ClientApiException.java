package com.example.atw.client.error;

import lombok.Getter;

@Getter
public class ClientApiException extends RuntimeException {

    private final int status;
    private final String code;
    private final String requestId;

    public ClientApiException(int status, String code, String message) {
        this(status, code, message, null);
    }

    public ClientApiException(int status, String code, String message, String requestId) {
        super(message);
        this.status = status;
        this.code = code;
        this.requestId = requestId;
    }

    public ClientApiException withRequestId(String requestId) {
        return new ClientApiException(status, code, getMessage(), requestId);
    }

    public static ClientApiException invalid(String message) {
        return new ClientApiException(400, ClientCodes.INVALID_ARGUMENT, message);
    }

    public static ClientApiException notFound(String code, String message) {
        return new ClientApiException(404, code, message);
    }

    public static ClientApiException configChanged(String message) {
        return new ClientApiException(409, ClientCodes.CONFIG_CHANGED, message);
    }

    public static ClientApiException upstream(String message) {
        return new ClientApiException(503, ClientCodes.SERVICE_UNAVAILABLE, message);
    }
}
