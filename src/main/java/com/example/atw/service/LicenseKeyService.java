package com.example.atw.service;

import com.example.atw.dto.LicensePayload;
import com.example.atw.exception.LicenseException;
import com.example.atw.utils.LicenseCryptoUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

/**
 * License 密钥与签名服务。
 * <p>
 * 启动时加载配置的密钥对；未配置且文件不存在时自动生成并落盘。
 * 生产环境建议把私钥部署在厂商侧（本服务仅加载，不生成）。
 */
@Service
public class LicenseKeyService {

    private static final Logger log = LoggerFactory.getLogger(LicenseKeyService.class);

    /** License 前缀，便于版本识别 */
    private static final String LICENSE_PREFIX = "ATW1";

    @Value("${license.public-key-path:}")
    private String publicKeyPath;

    @Value("${license.private-key-path:}")
    private String privateKeyPath;

    @Autowired
    private ObjectMapper objectMapper;

    private PublicKey publicKey;

    private PrivateKey privateKey;

    @PostConstruct
    public void init() {
        loadOrGenerateKeys();
    }

    private void loadOrGenerateKeys() {
        try {
            Path publicPath = trimToPath(publicKeyPath);
            Path privatePath = trimToPath(privateKeyPath);

            if (publicPath != null && privatePath != null
                    && Files.exists(publicPath) && Files.exists(privatePath)) {
                publicKey = LicenseCryptoUtil.decodePublicKey(readString(publicPath));
                privateKey = LicenseCryptoUtil.decodePrivateKey(readString(privatePath));
                log.info("已加载 License 密钥对: {}", publicPath);
                return;
            }

            KeyPair keyPair = LicenseCryptoUtil.generateKeyPair();
            publicKey = keyPair.getPublic();
            privateKey = keyPair.getPrivate();

            if (publicPath != null && privatePath != null) {
                writeString(publicPath, LicenseCryptoUtil.encodePublicKey(publicKey));
                writeString(privatePath, LicenseCryptoUtil.encodePrivateKey(privateKey));
                log.info("已生成并保存 License 密钥对: {}", publicPath);
                log.warn("警告：License 私钥已落盘于 {}，请妥善保管并做好访问控制；" +
                        "私钥只能部署在厂商签发服务端，切勿泄露或部署到客户机。", privatePath);
            }
        } catch (LicenseException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("License 密钥初始化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用私钥签发 License。
     */
    public String generate(LicensePayload payload) {
        if (privateKey == null) {
            throw new LicenseException("License 私钥不可用，无法签发");
        }
        try {
            byte[] payloadBytes = objectMapper.writeValueAsBytes(payload);
            String body = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes);
            byte[] signature = LicenseCryptoUtil.sign(payloadBytes, privateKey);
            String signatureText = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
            return LICENSE_PREFIX + "." + body + "." + signatureText;
        } catch (Exception e) {
            throw new LicenseException("License 签发失败: " + e.getMessage(), e);
        }
    }

    /**
     * 校验签名并解析 License 载荷。
     *
     * @throws LicenseException 格式错误或签名不合法
     */
    public LicensePayload parseAndVerify(String licenseKey) {
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            throw new LicenseException("License 授权码不能为空");
        }
        String key = licenseKey.trim().replaceAll("\\s+", "");
        String[] parts = key.split("\\.");
        if (parts.length != 3 || !LICENSE_PREFIX.equals(parts[0])) {
            throw new LicenseException("License 授权码格式无效");
        }
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            if (!LicenseCryptoUtil.verify(payloadBytes, signature, publicKey)) {
                throw new LicenseException("License 签名校验失败");
            }
            return objectMapper.readValue(payloadBytes, LicensePayload.class);
        } catch (LicenseException e) {
            throw e;
        } catch (Exception e) {
            throw new LicenseException("License 授权码解析失败", e);
        }
    }

    public boolean hasPrivateKey() {
        return privateKey != null;
    }

    private static Path trimToPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        return Paths.get(path.trim());
    }

    private static String readString(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static void writeString(Path path, String content) throws Exception {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }
}
