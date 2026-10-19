package com.example.atw.client.error;

public final class ClientErrors {

    private ClientErrors() {
    }

    public static ClientErrorResponse of(String requestId, String code, String message) {
        return new ClientErrorResponse(message, code, requestId);
    }
}
