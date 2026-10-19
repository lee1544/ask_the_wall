package com.example.atw.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * License 在线校验请求。
 */
@Data
public class VerifyLicenseRequest {

    @NotBlank(message = "授权码不能为空")
    private String licenseKey;

    /** 可选：上报本机机器码，用于一致性校验 */
    private String machineCode;
}
