package com.example.atw.client.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClientErrorResponse {

    private String error;
    private String code;
    private String requestId;
    private Integer retryAfterSeconds;

    public ClientErrorResponse(String error, String code, String requestId) {
        this.error = error;
        this.code = code;
        this.requestId = requestId;
    }
}
