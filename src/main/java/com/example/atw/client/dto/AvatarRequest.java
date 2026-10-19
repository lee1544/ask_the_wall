package com.example.atw.client.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class AvatarRequest extends ClientModelRequest {

    private String prompt;
    private String image;
    private String requestId;
}
