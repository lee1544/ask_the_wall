package com.example.atw.dto;

import lombok.Data;

/**
 * 激活成功后返回给客户端的 License。
 */
@Data
public class ActivateResponse {

    /** 授权码原文，客户端本地保存并用内置公钥验签 */
    private String licenseKey;

    private String licenseId;

    private String machineCode;

    private String licensee;

    private String edition;

    /** 签发时间（epoch 毫秒） */
    private Long issuedAt;

    /** 到期时间（epoch 毫秒），null 或 <=0 表示永久 */
    private Long expiresAt;
}
