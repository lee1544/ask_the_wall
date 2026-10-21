package com.example.atw.filter;

import com.example.atw.dto.LicensePayload;
import com.example.atw.exception.ApiResponse;
import com.example.atw.exception.LicenseException;
import com.example.atw.service.ActivationCodeService;
import com.example.atw.service.LicenseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 业务接口 License 二次校验过滤器。
 * <p>
 * 客户端激活后，业务请求需携带 {@code X-License: <授权码>}。除认证接口与 License 接口外，
 * 校验签名、有效期以及该 License 是否仍处于激活状态（支持服务端吊销），不通过时返回 403。
 */
@Component
public class LicenseFilter extends OncePerRequestFilter {

    public static final String LICENSE_HEADER = "X-License";

    @Autowired
    private LicenseService licenseService;

    @Autowired
    private ActivationCodeService activationCodeService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/v1/client/")) {
            response.setHeader("Cache-Control", "no-store");
        }

        if (!licenseService.isEnforceEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // 仅拦截业务接口；静态资源、错误页等放行
        if (uri == null || !uri.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }
        // 管理后台接口、认证接口、License 接口与能力查询始终放行
        if (isAdminApi(uri)
                || uri.startsWith("/api/license") || uri.startsWith("/api/auth")
                || uri.startsWith("/api/v1/client/capabilities")
                // 停止/状态查询/任务查询/临时下载不因 License 自然过期而阻断，仅校验登录身份与归属
                || uri.startsWith("/api/v1/client/requests/")
                || uri.startsWith("/api/v1/client/image-jobs/")
                || uri.startsWith("/api/v1/client/media/")
                || uri.matches("^/api/v1/client/chat/[^/]+/stop$")) {
            filterChain.doFilter(request, response);
            return;
        }

        String licenseKey = request.getHeader(LICENSE_HEADER);
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            reject(request, response, "LICENSE_REQUIRED", "缺少授权信息，请先激活 License");
            return;
        }

        try {
            LicensePayload payload = licenseService.verify(licenseKey);
            if (!activationCodeService.isActiveLicense(payload.getLicenseId())) {
                reject(request, response, "LICENSE_REQUIRED", "License 已被禁用或不存在");
                return;
            }
        } catch (LicenseException e) {
            String code = e.getMessage() != null && e.getMessage().contains("过期")
                    ? "LICENSE_EXPIRED" : "LICENSE_REQUIRED";
            reject(request, response, code, e.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAdminApi(String uri) {
        return uri.equals("/api/users") || uri.startsWith("/api/users/")
                || uri.equals("/api/roles") || uri.startsWith("/api/roles/")
                || uri.equals("/api/permissions") || uri.startsWith("/api/permissions/")
                || uri.equals("/api/organizations") || uri.startsWith("/api/organizations/");
    }

    private void reject(HttpServletRequest request, HttpServletResponse response,
                        String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (request.getRequestURI().startsWith("/api/v1/client/")) {
            String requestId = request.getHeader("X-Request-ID");
            response.getWriter().write(objectMapper.writeValueAsString(
                    com.example.atw.client.error.ClientErrors.of(requestId, code, message)));
            return;
        }
        response.getWriter().write(objectMapper.writeValueAsString(new ApiResponse<>(403, message)));
    }
}
