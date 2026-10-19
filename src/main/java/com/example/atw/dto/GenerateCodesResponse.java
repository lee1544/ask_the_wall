package com.example.atw.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量生成激活码响应。
 */
@Data
public class GenerateCodesResponse {

    private int count;

    private String batch;

    private List<String> codes;
}
