package com.example.atw.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 客户端模型接口配置。供应商密钥只存在服务端，不下发客户端。
 */
@Data
@Component
@ConfigurationProperties(prefix = "client")
public class ClientModelProperties {

    private Deepseek deepseek = new Deepseek();
    private Bailian bailian = new Bailian();
    private Chat chat = new Chat();
    private Asr asr = new Asr();
    private Tts tts = new Tts();
    private Image image = new Image();
    private Embedding embedding = new Embedding();
    private Media media = new Media();
    private Requests requests = new Requests();

    @Data
    public static class Deepseek {
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey = "";
        private String model = "deepseek-flash";
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 120000;
    }

    @Data
    public static class Bailian {
        private String origin = "https://dashscope.aliyuncs.com";
        private String apiKey = "";
        private int connectTimeoutMs = 10000;
    }

    @Data
    public static class Chat {
        private String spaceId = "chat-policy-v1";
        private int contextBudget = 8192;
        private int reservedOutputTokens = 1536;
        private int maxOutputTokens = 1536;
        private int maxRequestBytes = 2097152;
        private int maxMessages = 1024;
        private int maxResponseChars = 2000000;
        private int streamTimeoutMs = 180000;
    }

    @Data
    public static class Asr {
        private String model = "qwen3-asr-flash";
        private int maxBytes = 1920044;
        private int readTimeoutMs = 90000;
        private int maxChars = 16000;
    }

    @Data
    public static class Tts {
        private String model = "qwen3-tts-flash";
        private String voice = "Cherry";
        private String languageType = "Auto";
        private String segmentationVersion = "speech-segments-v1";
        private String configVersion = "voice-policy-v1";
        private int maxTextChars = 32000;
        private int readTimeoutMs = 90000;
        private int maxAudioBytes = 16 * 1024 * 1024;
    }

    @Data
    public static class Image {
        private String model = "wan2.7-image";
        private String configVersion = "image-policy-v1";
        private int maxPromptChars = 3000;
        private int maxReferenceBytes = 20971520;
        private int maxRequestBytes = 33554432;
        private int maxResultBytes = 20971520;
        private int readTimeoutMs = 180000;
        private int downloadTimeoutMs = 60000;
        private int maxSide = 8192;
        private long maxPixels = 32L * 1024 * 1024;
    }

    @Data
    public static class Embedding {
        private String model = "text-embedding-v4";
        private int dimensions = 1024;
        private String spaceId = "embedding-space-v1";
        private int maxBatchSize = 10;
        private int maxTextChars = 4000;
        private int maxResponseBytes = 2097152;
        private int readTimeoutMs = 60000;
    }

    @Data
    public static class Media {
        private String dir = "./data/media";
        private int ttlHours = 24;
    }

    @Data
    public static class Requests {
        private int retentionHours = 24;
    }
}
