package com.example.atw.client.error;

public final class ClientCodes {

    public static final String INVALID_ARGUMENT = "INVALID_ARGUMENT";
    public static final String CONTEXT_TOO_LARGE = "CONTEXT_TOO_LARGE";
    public static final String AUTH_EXPIRED = "AUTH_EXPIRED";
    public static final String LICENSE_REQUIRED = "LICENSE_REQUIRED";
    public static final String LICENSE_EXPIRED = "LICENSE_EXPIRED";
    public static final String REQUEST_NOT_FOUND = "REQUEST_NOT_FOUND";
    public static final String REQUEST_IN_PROGRESS = "REQUEST_IN_PROGRESS";
    public static final String REQUEST_CONFLICT = "REQUEST_CONFLICT";
    public static final String REQUEST_CANCELLED = "REQUEST_CANCELLED";
    public static final String REQUEST_EXPIRED = "REQUEST_EXPIRED";
    public static final String INDEX_SPACE_CHANGED = "INDEX_SPACE_CHANGED";
    public static final String CONFIG_CHANGED = "CONFIG_CHANGED";
    public static final String JOB_NOT_FOUND = "JOB_NOT_FOUND";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String RESOURCE_EXPIRED = "RESOURCE_EXPIRED";
    public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
    public static final String CONTENT_REJECTED = "CONTENT_REJECTED";
    public static final String NO_SPEECH = "NO_SPEECH";
    public static final String NO_READABLE_TEXT = "NO_READABLE_TEXT";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    public static final String UPSTREAM_TIMEOUT = "UPSTREAM_TIMEOUT";
    public static final String UPSTREAM_ERROR = "UPSTREAM_ERROR";
    public static final String QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ClientCodes() {
    }
}
