package com.example.atw.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AvatarJobDto {
    private String id;
    private String state;
    private String message;
    private String createdAt;
    private String url;
    private String expiresAt;
    private String error;
    private String code;
    private Integer pollAfterMs;
}
