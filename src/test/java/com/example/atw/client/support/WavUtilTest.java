package com.example.atw.client.support;

import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WavUtilTest {

    private static final int MAX_BYTES = 64 * 1024 * 1024;

    @Test
    void validatesSixteenKhzMonoRecording() {
        byte[] wav = wav(16000, 1, 16000);

        WavUtil.RecordingInfo info = WavUtil.validateRecording(wav, MAX_BYTES);

        assertEquals(1.0d, info.getSeconds(), 0.0001d);
        assertEquals(0.0305d, info.getRms(), 0.001d);
    }

    @Test
    void rejectsMismatchedDataLength() {
        byte[] wav = wav(16000, 1, 16000);
        ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(40, 16000 * 2 - 2);

        ClientApiException error = assertThrows(ClientApiException.class,
                () -> WavUtil.validateRecording(wav, MAX_BYTES));

        assertEquals(400, error.getStatus());
        assertEquals(ClientCodes.INVALID_ARGUMENT, error.getCode());
    }

    @Test
    void rejectsSilentRecording() {
        byte[] wav = wav(16000, 1, 16000, (short) 0);

        ClientApiException error = assertThrows(ClientApiException.class,
                () -> WavUtil.validateRecording(wav, MAX_BYTES));

        assertEquals(422, error.getStatus());
        assertEquals(ClientCodes.NO_SPEECH, error.getCode());
    }

    @Test
    void normalizesPlaceholderSizesFromSynthesizedAudio() {
        byte[] wav = wav(24000, 1, 24000);
        ByteBuffer source = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        source.putInt(4, 0x7fffffbf);
        source.putInt(40, 0x7fffff9b);

        byte[] fixed = WavUtil.normalizeSynthesized(wav, MAX_BYTES);
        ByteBuffer result = ByteBuffer.wrap(fixed).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(fixed.length - 8, result.getInt(4));
        assertEquals(fixed.length - 44, result.getInt(40));
    }

    @Test
    void keepsValidSynthesizedAudioUnchanged() {
        byte[] wav = wav(24000, 1, 24000);

        assertArrayEquals(wav, WavUtil.normalizeSynthesized(wav, MAX_BYTES));
    }

    @Test
    void rejectsMalformedSynthesizedAudioAsUpstreamFailure() {
        byte[] wav = wav(24000, 1, 24000);
        wav[0] = 'X';

        ClientApiException error = assertThrows(ClientApiException.class,
                () -> WavUtil.normalizeSynthesized(wav, MAX_BYTES));

        assertEquals(503, error.getStatus());
        assertEquals(ClientCodes.SERVICE_UNAVAILABLE, error.getCode());
    }

    private static byte[] wav(int sampleRate, int channels, int samples) {
        return wav(sampleRate, channels, samples, (short) 1000);
    }

    private static byte[] wav(int sampleRate, int channels, int samples, short sample) {
        int dataSize = samples * channels * 2;
        byte[] out = new byte[44 + dataSize];
        ByteBuffer buffer = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(ascii("RIFF"));
        buffer.putInt(out.length - 8);
        buffer.put(ascii("WAVE"));
        buffer.put(ascii("fmt "));
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) channels);
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * channels * 2);
        buffer.putShort((short) (channels * 2));
        buffer.putShort((short) 16);
        buffer.put(ascii("data"));
        buffer.putInt(dataSize);
        for (int i = 0; i < samples * channels; i++) {
            buffer.putShort(sample);
        }
        return out;
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
