package com.example.atw.client.support;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpeechTextChunkerTest {

    @Test
    void keepsShortTextAsSingleChunk() {
        assertEquals(Arrays.asList("short text"), SpeechTextChunker.chunk("short text", 20));
    }

    @Test
    void splitsAtSentenceBoundary() {
        List<String> chunks = SpeechTextChunker.chunk("\u4F60\u597D\u3002\u4E16\u754C\uFF01", 5);

        assertEquals(Arrays.asList("\u4F60\u597D\u3002", "\u4E16\u754C\uFF01"), chunks);
    }

    @Test
    void neverExceedsConfiguredLimit() {
        List<String> chunks = SpeechTextChunker.chunk("abcdefghij", 4);

        assertEquals(Arrays.asList("abcd", "efgh", "ij"), chunks);
    }

    @Test
    void keepsSurrogatePairsTogether() {
        String text = "\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00";

        List<String> chunks = SpeechTextChunker.chunk(text, 2);

        assertEquals(3, chunks.size());
        assertEquals(text, String.join("", chunks));
        for (String chunk : chunks) {
            assertEquals(2, chunk.length());
        }
    }
}
