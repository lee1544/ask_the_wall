package com.example.atw.utils;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * License 非对称签名工具：RSA 2048 + SHA256withRSA。
 * <p>
 * 签名端持有私钥，被授权端只持有公钥，保证 License 无法被伪造。
 */
public final class LicenseCryptoUtil {

    private static final String KEY_ALGORITHM = "RSA";

    private static final String SIGN_ALGORITHM = "SHA256withRSA";

    private static final int KEY_SIZE = 2048;

    private LicenseCryptoUtil() {
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            generator.initialize(KEY_SIZE);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("生成 RSA 密钥对失败", e);
        }
    }

    public static byte[] sign(byte[] data, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance(SIGN_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(data);
            return signature.sign();
        } catch (Exception e) {
            throw new IllegalStateException("License 签名失败", e);
        }
    }

    public static boolean verify(byte[] data, byte[] signatureBytes, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance(SIGN_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(data);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }

    public static String encodePublicKey(PublicKey publicKey) {
        return toPem("PUBLIC KEY", publicKey.getEncoded());
    }

    public static String encodePrivateKey(PrivateKey privateKey) {
        return toPem("PRIVATE KEY", privateKey.getEncoded());
    }

    public static PublicKey decodePublicKey(String pem) {
        try {
            byte[] der = fromPem(pem);
            return KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalArgumentException("解析公钥失败", e);
        }
    }

    public static PrivateKey decodePrivateKey(String pem) {
        try {
            byte[] der = fromPem(pem);
            return KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalArgumentException("解析私钥失败", e);
        }
    }

    private static String toPem(String type, byte[] der) {
        String base64 = Base64.getEncoder().encodeToString(der);
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN ").append(type).append("-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
        }
        sb.append("-----END ").append(type).append("-----\n");
        return sb.toString();
    }

    private static byte[] fromPem(String pem) {
        String content = pem.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(content);
    }
}
