package com.example.atw.service;

import com.example.atw.dto.*;
import com.example.atw.entity.ActivationCode;
import com.example.atw.exception.BusinessException;
import com.example.atw.exception.LicenseException;
import com.example.atw.exception.NotFoundException;
import com.example.atw.repository.ActivationCodeRepository;
import com.example.atw.utils.MachineCodeUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 激活码服务：批量生成、激活（一码一机）、状态管理与吊销校验。
 */
@Service
public class ActivationCodeService {

    /** 去除易混淆字符（I/O/0/1）后的字符集 */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int CODE_LENGTH = 16;

    private final SecureRandom random = new SecureRandom();

    @Autowired
    private ActivationCodeRepository activationCodeRepository;

    @Autowired
    private LicenseService licenseService;

    /** 已签发 License 的可用性缓存（仅缓存 positive，吊销时主动失效） */
    private final Cache<String, Boolean> licenseActiveCache = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    /**
     * 批量生成激活码。
     */
    @Transactional
    public List<String> generateCodes(GenerateCodesRequest request) {
        int count = request.getCount();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = (request.getDeadlineDays() != null && request.getDeadlineDays() > 0)
                ? now.plusDays(request.getDeadlineDays())
                : null;

        List<ActivationCode> entities = new ArrayList<>(count);
        Set<String> generated = new HashSet<>();
        while (entities.size() < count) {
            String code = randomCode();
            if (generated.contains(code) || activationCodeRepository.existsByCode(code)) {
                continue;
            }
            generated.add(code);

            ActivationCode entity = new ActivationCode();
            entity.setCode(code);
            entity.setStatus(ActivationCode.STATUS_UNUSED);
            entity.setBatch(request.getBatch());
            entity.setLicensee(request.getLicensee());
            entity.setEdition(request.getEdition());
            entity.setValidDays(request.getValidDays());
            entity.setDeadlineAt(deadline);
            entity.setRemark(request.getRemark());
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            entities.add(entity);
        }

        activationCodeRepository.saveAll(entities);
        return entities.stream().map(ActivationCode::getCode).collect(Collectors.toList());
    }

    /**
     * 激活：校验激活码，签发绑定机器码的 License。
     * <p>
     * 一码一机：激活码已绑定其他机器码时拒绝；绑定本机时幂等返回原 License。
     */
    @Transactional
    public ActivateResponse activate(ActivateRequest request) {
        String machineCode = MachineCodeUtil.normalize(request.getMachineCode());
        if (machineCode.isEmpty()) {
            throw new LicenseException("机器码不能为空");
        }

        String code = canonicalizeCode(request.getActivationCode());
        ActivationCode entity = activationCodeRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new LicenseException("激活码无效"));

        if (entity.getStatus() != null && entity.getStatus() == ActivationCode.STATUS_DISABLED) {
            throw new LicenseException("激活码已被禁用");
        }

        if (entity.getStatus() != null && entity.getStatus() == ActivationCode.STATUS_ACTIVATED) {
            if (machineCode.equalsIgnoreCase(entity.getMachineCode())) {
                LicensePayload payload = licenseService.parse(entity.getLicenseKey());
                return toResponse(entity.getLicenseKey(), payload);
            }
            throw new LicenseException("激活码已被其他机器使用");
        }

        if (entity.getDeadlineAt() != null && LocalDateTime.now().isAfter(entity.getDeadlineAt())) {
            throw new LicenseException("激活码已过期");
        }

        IssuedLicense issued = licenseService.issue(
                machineCode, entity.getLicensee(), entity.getEdition(), entity.getValidDays());

        entity.setStatus(ActivationCode.STATUS_ACTIVATED);
        entity.setMachineCode(issued.getPayload().getMachineCode());
        entity.setLicenseId(issued.getPayload().getLicenseId());
        entity.setLicenseKey(issued.getLicenseKey());
        entity.setActivatedAt(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        activationCodeRepository.save(entity);

        licenseActiveCache.put(issued.getPayload().getLicenseId(), Boolean.TRUE);
        return toResponse(issued.getLicenseKey(), issued.getPayload());
    }

    public Page<ActivationCode> list(Integer status, String batch, String keyword, Pageable pageable) {
        return activationCodeRepository.search(status, trimToNull(batch), trimToNull(keyword), pageable);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Transactional
    public ActivationCode updateStatus(Integer id, Integer status) {
        if (status == null || (status != ActivationCode.STATUS_UNUSED
                && status != ActivationCode.STATUS_ACTIVATED
                && status != ActivationCode.STATUS_DISABLED)) {
            throw new BusinessException("状态值非法");
        }
        ActivationCode entity = activationCodeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("激活码不存在"));
        entity.setStatus(status);
        entity.setUpdateTime(LocalDateTime.now());
        activationCodeRepository.save(entity);

        if (entity.getLicenseId() != null) {
            licenseActiveCache.invalidate(entity.getLicenseId());
        }
        return entity;
    }

    /**
     * 判断某 License 是否仍处于激活（未禁用）状态，用于业务接口二次校验与吊销。
     */
    public boolean isActiveLicense(String licenseId) {
        if (licenseId == null || licenseId.trim().isEmpty()) {
            return false;
        }
        Boolean cached = licenseActiveCache.getIfPresent(licenseId);
        if (cached != null) {
            return cached;
        }
        boolean active = activationCodeRepository.findByLicenseId(licenseId)
                .map(code -> code.getStatus() != null && code.getStatus() == ActivationCode.STATUS_ACTIVATED)
                .orElse(false);
        if (active) {
            licenseActiveCache.put(licenseId, Boolean.TRUE);
        }
        return active;
    }

    private ActivateResponse toResponse(String licenseKey, LicensePayload payload) {
        ActivateResponse response = new ActivateResponse();
        response.setLicenseKey(licenseKey);
        response.setLicenseId(payload.getLicenseId());
        response.setMachineCode(MachineCodeUtil.format(payload.getMachineCode()));
        response.setLicensee(payload.getLicensee());
        response.setEdition(payload.getEdition());
        response.setIssuedAt(payload.getIssuedAt());
        response.setExpiresAt(payload.getExpiresAt());
        return response;
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH + CODE_LENGTH / 4);
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * 归一化用户输入的激活码为规范形式（大写、每 4 位一组）。
     */
    private String canonicalizeCode(String input) {
        String raw = input == null ? "" : input.replaceAll("[^0-9A-Za-z]", "").toUpperCase();
        StringBuilder sb = new StringBuilder(raw.length() + raw.length() / 4);
        for (int i = 0; i < raw.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append('-');
            }
            sb.append(raw.charAt(i));
        }
        return sb.toString();
    }
}
