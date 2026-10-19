package com.example.atw.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 客户端激活请求：提交激活码与本机机器码。
 */
@Data
public class ActivateRequest {

    @NotBlank(message = "激活码不能为空")
    private String activationCode;

    @NotBlank(message = "机器码不能为空")
    private String machineCode;
}
