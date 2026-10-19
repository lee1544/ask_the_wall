package com.example.atw.client.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class SpeechSynthesizeRequest extends ClientModelRequest {

    private String text;
    private Integer segmentIndex;
    private String requestId;
    private String expectedConfigVersion;
}
