package com.example.atw.dto;

import lombok.Data;

/**
 * License 载荷：签名前被序列化为 JSON，激活时原样校验签名。
 */
@Data
public class LicensePayload {

    /** License 唯一编号 */
    private String licenseId;

    /** 绑定的机器码（归一化后的大写十六进制） */
    private String machineCode;

    /** 被授权方名称 */
    private String licensee;

    /** 版本 / 授权类型，如 standard、professional */
    private String edition;

    /** 签发时间（epoch 毫秒） */
    private long issuedAt;

    /** 到期时间（epoch 毫秒），小于等于 0 表示永久授权 */
    private long expiresAt;
}
