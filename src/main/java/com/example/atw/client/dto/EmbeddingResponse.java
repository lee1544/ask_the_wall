package com.example.atw.client.dto;

import lombok.Data;

import java.util.List;

@Data
public class EmbeddingResponse {
    private List<List<Double>> vectors;
    private int dimensions;
    private String spaceId;
    private Object usage;
    private String requestId;
}
