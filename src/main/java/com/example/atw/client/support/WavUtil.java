package com.example.atw.client.support;

import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class WavUtil {

    private static final Set<Integer> ALLOWED_RATES = new HashSet<>(
            Arrays.asList(16000, 22050, 24000, 44100, 48000));

    private static final long PLACEHOLDER_RIFF = 0x7fffffbfL;
    private static final long PLACEHOLDER_DATA = 0x7fffff9bL;

    private WavUtil() {
    }

    public static RecordingInfo validateRecording(byte[] data, int maxBytes) {
        if (data == null || data.length < 44) {
            throw new ClientApiException(400, ClientCodes.INVALID_ARGUMENT, "录音不是有效的 WAV 文件");
        }
        if (data.length > maxBytes) {
            throw new ClientApiException(413, ClientCodes.PAYLOAD_TOO_LARGE, "录音文件过大");
        }
        WavInfo info = parse(data);
        if (info.audioFormat != 1 || info.bitsPerSample != 16) {
            throw new ClientApiException(400, ClientCodes.INVALID_ARGUMENT, "录音必须为 PCM 16-bit");
        }
        if (info.channels != 1 || info.sampleRate != 16000) {
            throw new ClientApiException(400, ClientCodes.INVALID_ARGUMENT, "录音必须为 16000Hz 单声道");
        }
        long actualData = data.length - info.dataOffset;
        if (actualData <= 0 || actualData != info.dataSize) {
            throw new ClientApiException(400, ClientCodes.INVALID_ARGUMENT, "录音长度与 WAV 头不一致");
        }
        if (info.blockAlign <= 0 || info.byteRate <= 0 || actualData % info.blockAlign != 0) {
            throw new ClientApiException(400, ClientCodes.INVALID_ARGUMENT, "录音数据未对齐");
        }
        double seconds = (double) actualData / info.byteRate;
        if (seconds < 0.1 || seconds > 60.0) {
            throw new ClientApiException(400, ClientCodes.INVALID_ARGUMENT, "录音时长必须在 0.1-60 秒之间");
        }
        double rms = rms(data, info.dataOffset, (int) actualData);
        if (rms <= 0.001d) {
            throw new ClientApiException(422, ClientCodes.NO_SPEECH, "没有检测到有效语音");
        }
        return new RecordingInfo(seconds, rms);
    }

    public static byte[] normalizeSynthesized(byte[] data, int maxBytes) {
        if (data == null || data.length < 44) {
            throw upstream("供应商音频不是有效的 WAV 文件");
        }
        if (data.length > maxBytes) {
            throw new ClientApiException(413, ClientCodes.PAYLOAD_TOO_LARGE, "供应商音频超过大小限制");
        }
        WavInfo info;
        try {
            info = parse(data);
        } catch (ClientApiException e) {
            throw upstream(e.getMessage());
        }
        if (info.audioFormat != 1 || info.bitsPerSample != 16) {
            throw upstream("供应商音频必须为 PCM 16-bit");
        }
        if (info.channels != 1 && info.channels != 2) {
            throw upstream("供应商音频声道数不受支持");
        }
        if (!ALLOWED_RATES.contains(info.sampleRate)) {
            throw upstream("供应商音频采样率不受支持");
        }
        if (info.blockAlign != info.channels * 2 || info.byteRate != info.sampleRate * info.blockAlign) {
            throw upstream("供应商音频 fmt 校验失败");
        }
        long actualData = data.length - info.dataOffset;
        if (actualData <= 0 || actualData % info.blockAlign != 0) {
            throw upstream("供应商音频数据未对齐");
        }

        boolean placeholder = info.dataOffset == 44
                && info.fmtOffset == 12
                && info.fmtSize == 16
                && info.riffSize == PLACEHOLDER_RIFF
                && info.dataSize == PLACEHOLDER_DATA;
        if (placeholder) {
            byte[] fixed = data.clone();
            writeLe32(fixed, 4, (int) (data.length - 8));
            writeLe32(fixed, info.dataOffset - 4, (int) (data.length - 44));
            return fixed;
        }

        if (info.riffSize != data.length - 8 || info.dataSize != actualData) {
            throw upstream("供应商音频长度与实际数据不一致");
        }
        return data;
    }

    public static byte[] concatSynthesized(List<byte[]> parts, int maxBytes) {
        if (parts == null || parts.isEmpty()) {
            throw upstream("供应商音频为空");
        }
        if (parts.size() == 1) {
            return normalizeSynthesized(parts.get(0), maxBytes);
        }

        byte[][] normalized = new byte[parts.size()][];
        WavInfo[] infos = new WavInfo[parts.size()];
        long totalData = 0L;
        WavInfo reference = null;
        for (int index = 0; index < parts.size(); index++) {
            byte[] part = normalizeSynthesized(parts.get(index), maxBytes);
            WavInfo info = parse(part);
            if (info.audioFormat != 1 || info.bitsPerSample != 16) {
                throw upstream("供应商音频必须为 PCM 16-bit");
            }
            long actualData = part.length - info.dataOffset;
            if (info.dataSize != actualData || actualData <= 0) {
                throw upstream("供应商音频长度与实际数据不一致");
            }
            if (reference == null) {
                reference = info;
            } else if (reference.channels != info.channels
                    || reference.sampleRate != info.sampleRate
                    || reference.blockAlign != info.blockAlign) {
                throw upstream("供应商音频格式不一致");
            }
            normalized[index] = part;
            infos[index] = info;
            totalData += actualData;
        }

        long outputLength = 44L + totalData;
        if (outputLength > maxBytes) {
            throw new ClientApiException(413, ClientCodes.PAYLOAD_TOO_LARGE, "供应商音频超过大小限制");
        }

        byte[] result = new byte[(int) outputLength];
        writeAscii(result, 0, "RIFF");
        writeLe32(result, 4, (int) outputLength - 8);
        writeAscii(result, 8, "WAVE");
        writeAscii(result, 12, "fmt ");
        writeLe32(result, 16, 16);
        writeLe16(result, 20, reference.audioFormat);
        writeLe16(result, 22, reference.channels);
        writeLe32(result, 24, reference.sampleRate);
        writeLe32(result, 28, reference.sampleRate * reference.blockAlign);
        writeLe16(result, 32, reference.blockAlign);
        writeLe16(result, 34, reference.bitsPerSample);
        writeAscii(result, 36, "data");
        writeLe32(result, 40, (int) totalData);

        int position = 44;
        for (int index = 0; index < normalized.length; index++) {
            int size = (int) infos[index].dataSize;
            System.arraycopy(normalized[index], infos[index].dataOffset, result, position, size);
            position += size;
        }
        return result;
    }

    private static WavInfo parse(byte[] data) {
        if (!asciiEquals(data, 0, "RIFF") || !asciiEquals(data, 8, "WAVE")) {
            throw new ClientApiException(400, ClientCodes.INVALID_ARGUMENT, "文件不是 RIFF/WAVE 格式");
        }
        WavInfo info = new WavInfo();
        info.riffSize = le32(data, 4);
        int pos = 12;
        while (pos + 8 <= data.length) {
            if (asciiEquals(data, pos, "fmt ")) {
                int size = le32Int(data, pos + 4);
                int content = pos + 8;
                if (size < 16 || content + 16 > data.length) {
                    throw new ClientApiException(400, ClientCodes.INVALID_ARGUMENT, "WAV fmt 块损坏");
                }
                info.fmtOffset = pos;
                info.fmtSize = size;
                info.audioFormat = le16(data, content);
                info.channels = le16(data, content + 2);
                info.sampleRate = le32Int(data, content + 4);
                info.byteRate = le32Int(data, content + 8);
                info.blockAlign = le16(data, content + 12);
                info.bitsPerSample = le16(data, content + 14);
            } else if (asciiEquals(data, pos, "data")) {
                info.dataOffset = pos + 8;
                info.dataSize = le32(data, pos + 4);
                return info;
            }
            int size = le32Int(data, pos + 4);
            if (size < 0) {
                break;
            }
            pos = pos + 8 + size + (size % 2);
        }
        throw new ClientApiException(400, ClientCodes.INVALID_ARGUMENT, "WAV 缺少 data 块");
    }

    private static double rms(byte[] data, int offset, int length) {
        long samples = length / 2;
        if (samples <= 0) {
            return 0d;
        }
        double sum = 0d;
        for (int i = 0; i < length - 1; i += 2) {
            int low = data[offset + i] & 0xFF;
            int high = data[offset + i + 1];
            short sample = (short) ((high << 8) | low);
            double normalized = sample / 32768.0d;
            sum += normalized * normalized;
        }
        return Math.sqrt(sum / samples);
    }

    private static boolean asciiEquals(byte[] data, int offset, String value) {
        if (offset + value.length() > data.length) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (data[offset + i] != (byte) value.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int le16(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static long le32(byte[] data, int offset) {
        return (data[offset] & 0xFFL)
                | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16)
                | ((data[offset + 3] & 0xFFL) << 24);
    }

    private static int le32Int(byte[] data, int offset) {
        return (int) le32(data, offset);
    }

    private static void writeLe16(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >>> 8) & 0xFF);
    }

    private static void writeLe32(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        data[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        data[offset + 3] = (byte) ((value >>> 24) & 0xFF);
    }

    private static void writeAscii(byte[] data, int offset, String value) {
        for (int index = 0; index < value.length(); index++) {
            data[offset + index] = (byte) value.charAt(index);
        }
    }

    private static ClientApiException upstream(String message) {
        return new ClientApiException(503, ClientCodes.SERVICE_UNAVAILABLE, message);
    }

    private static class WavInfo {
        private int audioFormat;
        private int channels;
        private int sampleRate;
        private int byteRate;
        private int blockAlign;
        private int bitsPerSample;
        private int fmtOffset = -1;
        private long fmtSize;
        private int dataOffset = -1;
        private long dataSize;
        private long riffSize;
    }

    public static class RecordingInfo {
        private final double seconds;
        private final double rms;

        public RecordingInfo(double seconds, double rms) {
            this.seconds = seconds;
            this.rms = rms;
        }

        public double getSeconds() {
            return seconds;
        }

        public double getRms() {
            return rms;
        }
    }
}
