package com.example.atw.client.support;

import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

public final class ImageUtil {

    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private ImageUtil() {
    }

    public static byte[] decodeJpegDataUrl(String dataUrl, int maxBytes) {
        if (dataUrl == null || dataUrl.isEmpty()) {
            return null;
        }
        String prefix = "data:image/jpeg;base64,";
        if (!dataUrl.startsWith(prefix)) {
            throw new ClientApiException(400, ClientCodes.INVALID_ARGUMENT, "参考图必须为 JPEG Data URL");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(dataUrl.substring(prefix.length()));
        } catch (IllegalArgumentException e) {
            throw new ClientApiException(400, ClientCodes.INVALID_ARGUMENT, "参考图 Base64 无效");
        }
        if (decoded.length > maxBytes) {
            throw new ClientApiException(413, ClientCodes.PAYLOAD_TOO_LARGE, "参考图超过 20 MiB");
        }
        if (decoded.length < 4 || (decoded[0] & 0xFF) != 0xFF || (decoded[1] & 0xFF) != 0xD8
                || (decoded[2] & 0xFF) != 0xFF) {
            throw new ClientApiException(400, ClientCodes.INVALID_ARGUMENT, "参考图不是有效的 JPEG");
        }
        BufferedImage image = read(decoded);
        if (image == null) {
            throw new ClientApiException(400, ClientCodes.INVALID_ARGUMENT, "参考图无法解码");
        }
        int width = image.getWidth();
        int height = image.getHeight();
        if (width < 240 || height < 240 || width > 8000 || height > 8000) {
            throw new ClientApiException(400, ClientCodes.INVALID_ARGUMENT, "参考图宽高必须在 240-8000 之间");
        }
        double ratio = (double) Math.max(width, height) / Math.min(width, height);
        if (ratio > 8d) {
            throw new ClientApiException(400, ClientCodes.INVALID_ARGUMENT, "参考图宽高比不能超过 8:1");
        }
        return decoded;
    }

    public static ImageSize validatePng(byte[] data, int maxBytes, int maxSide, long maxPixels) {
        if (data == null || data.length < 8) {
            throw upstream("供应商图片不是有效的 PNG");
        }
        if (data.length > maxBytes) {
            throw upstream("供应商图片超过大小限制");
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) {
                throw upstream("供应商图片不是有效的 PNG");
            }
        }
        BufferedImage image = read(data);
        if (image == null) {
            throw upstream("供应商图片无法解码");
        }
        int width = image.getWidth();
        int height = image.getHeight();
        if (width > maxSide || height > maxSide || (long) width * height > maxPixels) {
            throw upstream("供应商图片尺寸超出限制");
        }
        return new ImageSize(width, height);
    }

    private static BufferedImage read(byte[] data) {
        try {
            return ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException e) {
            return null;
        }
    }

    private static ClientApiException upstream(String message) {
        return new ClientApiException(503, ClientCodes.SERVICE_UNAVAILABLE, message);
    }

    public static class ImageSize {
        private final int width;
        private final int height;

        public ImageSize(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }
}
