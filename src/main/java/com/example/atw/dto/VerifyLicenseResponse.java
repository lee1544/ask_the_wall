package com.example.atw.dto;

import lombok.Data;

/**
 * License 在线校验结果。
 */
@Data
public class VerifyLicenseResponse {

    private boolean valid;

    private String message;

    private String licenseId;

    private String machineCode;

    private String licensee;

    private String edition;

    /** 签发时间（epoch 毫秒） */
    private Long issuedAt;

    /** 到期时间（epoch 毫秒），null 或 <=0 表示永久 */
    private Long expiresAt;

    /** 是否已过期 */
    private boolean expired;

    public static VerifyLicenseResponse of(LicensePayload payload) {
        VerifyLicenseResponse response = new VerifyLicenseResponse();
        response.setValid(true);
        response.setMessage("License 有效");
        response.setLicenseId(payload.getLicenseId());
        response.setMachineCode(payload.getMachineCode());
        response.setLicensee(payload.getLicensee());
        response.setEdition(payload.getEdition());
        response.setIssuedAt(payload.getIssuedAt());
        response.setExpiresAt(payload.getExpiresAt());
        return response;
    }

    public static VerifyLicenseResponse invalid(String message) {
        VerifyLicenseResponse response = new VerifyLicenseResponse();
        response.setValid(false);
        response.setMessage(message);
        return response;
    }
}
