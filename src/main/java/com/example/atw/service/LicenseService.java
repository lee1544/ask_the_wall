package com.example.atw.service;

import com.example.atw.dto.IssuedLicense;
import com.example.atw.dto.LicensePayload;
import com.example.atw.exception.LicenseException;
import com.example.atw.utils.MachineCodeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * License 签发与校验服务（厂商侧）。
 * <p>
 * 服务端持有私钥，根据客户端上报的机器码签发绑定该机器码的 License；
 * 业务接口通过 {@code X-License} 请求头携带授权码，由 {@link com.example.atw.filter.LicenseFilter} 校验。
 */
@Service
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);

    private static final long MILLIS_PER_DAY = 24L * 60 * 60 * 1000;

    /** 是否开启业务接口的 License 强制校验 */
    @Value("${license.enforce:true}")
    private boolean enforce;

    @Autowired
    private LicenseKeyService licenseKeyService;

    public boolean isEnforceEnabled() {
        return enforce;
    }

    /**
     * 签发 License。
     *
     * @param machineCode 客户端机器码（内部会归一化）
     * @param licensee    被授权方名称
     * @param edition     版本 / 授权类型
     * @param validDays   有效天数，null 或 <=0 表示永久
     */
    public IssuedLicense issue(String machineCode, String licensee, String edition, Integer validDays) {
        String normalized = MachineCodeUtil.normalize(machineCode);
        if (normalized.isEmpty()) {
            throw new LicenseException("机器码不能为空");
        }

        LicensePayload payload = new LicensePayload();
        payload.setLicenseId(UUID.randomUUID().toString());
        payload.setMachineCode(normalized);
        payload.setLicensee(licensee == null ? "" : licensee.trim());
        payload.setEdition((edition == null || edition.trim().isEmpty()) ? "standard" : edition.trim());
        long now = System.currentTimeMillis();
        payload.setIssuedAt(now);
        payload.setExpiresAt(validDays != null && validDays > 0 ? now + validDays * MILLIS_PER_DAY : 0L);

        String licenseKey = licenseKeyService.generate(payload);
        log.info("License 签发成功: licenseId={}, licensee={}, machineCode={}",
                payload.getLicenseId(), payload.getLicensee(), MachineCodeUtil.format(payload.getMachineCode()));
        return new IssuedLicense(licenseKey, payload);
    }

    /**
     * 校验签名与有效期。
     *
     * @throws LicenseException 签名非法或已过期
     */
    public LicensePayload verify(String licenseKey) {
        LicensePayload payload = licenseKeyService.parseAndVerify(licenseKey);
        if (isExpired(payload)) {
            throw new LicenseException("License 已过期");
        }
        return payload;
    }

    /**
     * 仅校验签名（不校验有效期）。
     */
    public LicensePayload parse(String licenseKey) {
        return licenseKeyService.parseAndVerify(licenseKey);
    }

    public boolean isExpired(LicensePayload payload) {
        return payload.getExpiresAt() > 0 && System.currentTimeMillis() > payload.getExpiresAt();
    }
}
