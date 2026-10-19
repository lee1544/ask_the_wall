package com.example.atw.web;

import com.example.atw.annotation.RequirePermission;
import com.example.atw.dto.*;
import com.example.atw.entity.ActivationCode;
import com.example.atw.exception.LicenseException;
import com.example.atw.service.ActivationCodeService;
import com.example.atw.service.LicenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/license")
public class LicenseController {

    @Autowired
    private ActivationCodeService activationCodeService;

    @Autowired
    private LicenseService licenseService;

    /**
     * 公开：客户端输入激活码激活（服务端签发绑定机器码的 License）。
     */
    @PostMapping("/activate")
    public ActivateResponse activate(@Valid @RequestBody ActivateRequest request) {
        return activationCodeService.activate(request);
    }

    /**
     * 公开：在线校验 License（签名 + 有效期 + 激活状态）。
     */
    @PostMapping("/verify")
    public VerifyLicenseResponse verify(@Valid @RequestBody VerifyLicenseRequest request) {
        try {
            LicensePayload payload = licenseService.verify(request.getLicenseKey());
            if (!activationCodeService.isActiveLicense(payload.getLicenseId())) {
                return VerifyLicenseResponse.invalid("License 已被禁用或不存在");
            }
            return VerifyLicenseResponse.of(payload);
        } catch (LicenseException e) {
            return VerifyLicenseResponse.invalid(e.getMessage());
        }
    }

    /**
     * 厂商侧：批量生成激活码。
     */
    @RequirePermission("license:create-code")
    @PostMapping("/codes")
    public GenerateCodesResponse generateCodes(@Valid @RequestBody GenerateCodesRequest request) {
        List<String> codes = activationCodeService.generateCodes(request);
        GenerateCodesResponse response = new GenerateCodesResponse();
        response.setCount(codes.size());
        response.setBatch(request.getBatch());
        response.setCodes(codes);
        return response;
    }

    /**
     * 厂商侧：分页查询激活码。
     */
    @RequirePermission("license:view-code")
    @GetMapping("/codes")
    public PageResponse<ActivationCode> listCodes(
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "batch", required = false) String batch,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        Page<ActivationCode> codes = activationCodeService.list(status, batch, PageRequest.of(page, size));
        return PageResponse.of(codes.getContent(), codes.getNumber(), codes.getSize(), codes.getTotalElements());
    }

    /**
     * 厂商侧：启用/禁用激活码（禁用后已签发的 License 立即失效）。
     */
    @RequirePermission("license:create-code")
    @PutMapping("/codes/{id}/status")
    public ActivationCode updateStatus(@PathVariable Integer id, @RequestParam("status") Integer status) {
        return activationCodeService.updateStatus(id, status);
    }
}
