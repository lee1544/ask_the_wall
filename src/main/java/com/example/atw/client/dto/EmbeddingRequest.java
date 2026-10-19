package com.example.atw.client.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class EmbeddingRequest extends ClientModelRequest {

    private List<String> texts;
    private String textType;
    private String expectedSpaceId;
    private String requestId;
}
