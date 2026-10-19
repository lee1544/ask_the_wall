package com.example.atw.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 内部使用：签发结果（授权码原文 + 载荷）。
 */
@Data
@AllArgsConstructor
public class IssuedLicense {

    private String licenseKey;

    private LicensePayload payload;
}
