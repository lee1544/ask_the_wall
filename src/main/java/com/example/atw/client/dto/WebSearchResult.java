package com.example.atw.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSearchResult {

    private String title;
    private String url;
    private String snippet;
    private String publishedAt;
    private String siteName;
    private Double score;
    private Integer authority;
}
