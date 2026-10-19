package com.example.atw.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class RequestStatusResponse {
    private String requestId;
    private String operation;
    private String generationId;
    private String state;
    private Integer httpStatus;
    private JsonNode result;
    private String error;
    private String code;
    private String expiresAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String partialContent;
}
