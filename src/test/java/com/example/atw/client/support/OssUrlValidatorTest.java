package com.example.atw.client.support;

import com.example.atw.client.error.ClientApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OssUrlValidatorTest {

    @Test
    void keepsSignedQueryUntouchedWhenUpgradingToHttps() {
        String signed = "http://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/prod/a.wav"
                + "?Expires=1792573105&Signature=3C5W0%2B51px%2FkW%3D";

        String secure = OssUrlValidator.toSecureUrl(signed);

        assertEquals("https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/prod/a.wav"
                + "?Expires=1792573105&Signature=3C5W0%2B51px%2FkW%3D", secure);
    }

    @Test
    void keepsHttpsUrlAsIs() {
        String signed = "https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/prod/a.png"
                + "?Signature=a%2Bb%3D";

        assertEquals(signed, OssUrlValidator.toSecureUrl(signed));
    }

    @Test
    void rejectsUntrustedHost() {
        assertThrows(ClientApiException.class,
                () -> OssUrlValidator.toSecureUrl("https://example.com/a.png"));
    }

    @Test
    void rejectsCredentialsAndFragment() {
        assertThrows(ClientApiException.class,
                () -> OssUrlValidator.toSecureUrl("https://user@dashscope-result-bj.oss-cn-beijing.aliyuncs.com/a.png"));
        assertThrows(ClientApiException.class,
                () -> OssUrlValidator.toSecureUrl("https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/a.png#x"));
    }
}
