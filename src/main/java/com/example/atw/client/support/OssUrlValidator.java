package com.example.atw.client.support;

import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;

import java.net.URI;
import java.net.URISyntaxException;

public final class OssUrlValidator {

    private OssUrlValidator() {
    }

    /**
     * 仅允许官方 OSS HTTPS 地址：无凭据、无显式端口、禁止重定向（由调用方保证）。
     * 保留原始路径与查询串，避免重新编码破坏 OSS 签名中的 %2B/%2F/%3D。
     */
    public static String toSecureUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw upstream("供应商未返回下载地址");
        }
        String trimmed = url.trim();
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw upstream("供应商下载地址无效");
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            throw upstream("供应商下载地址无效");
        }
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw upstream("供应商下载地址协议不受支持");
        }
        if (uri.getRawUserInfo() != null || uri.getPort() != -1 || uri.getRawFragment() != null) {
            throw upstream("供应商下载地址不安全");
        }
        if (!host.matches("(?i)^[a-z0-9.-]+\\.oss-[a-z0-9-]+\\.aliyuncs\\.com$")) {
            throw upstream("供应商下载地址不在可信域名内");
        }
        if (uri.getRawPath() == null || uri.getRawPath().isEmpty()) {
            throw upstream("供应商下载地址无效");
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return "https://" + trimmed.substring(trimmed.indexOf("://") + 3);
        }
        return trimmed;
    }

    private static ClientApiException upstream(String message) {
        return new ClientApiException(503, ClientCodes.SERVICE_UNAVAILABLE, message);
    }
}
