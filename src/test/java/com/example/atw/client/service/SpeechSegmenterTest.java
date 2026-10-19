package com.example.atw.client.service;

import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeechSegmenterTest {

    private final SpeechSegmenter segmenter = new SpeechSegmenter();

    @Test
    void stripsBoldMarkersAndKeepsPunctuation() {
        assertEquals(Collections.singletonList("你好，世界！"), segmenter.segment("**你好**，世界！"));
    }

    @Test
    void dropsCodeBlocks() {
        assertEquals(Collections.singletonList("正文。"),
                segmenter.segment("正文。\n\n```java\nint a = 1;\n```\n"));
    }

    @Test
    void firstSegmentStopsAtSentenceBoundaryAfterOneHundredTwentyUnits() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            text.append('甲');
        }
        text.append("。乙。");

        List<String> segments = segmenter.segment(text.toString());

        assertEquals(2, segments.size());
        assertEquals(41, segments.get(0).length());
        assertEquals("乙。", segments.get(1));
    }

    @Test
    void rejectsWhenNothingReadableRemains() {
        ClientApiException error = assertThrows(ClientApiException.class,
                () -> segmenter.segment("```\nint a = 1;\n```"));

        assertEquals(422, error.getStatus());
        assertEquals(ClientCodes.NO_READABLE_TEXT, error.getCode());
    }

    @Test
    void stripsMarkdownMarkersLinksAndUrls() {
        String joined = String.join("", segmenter.segment(
                "# 标题\n\n> 引用\n\n- 列表项。\n\n[链接](https://example.com)\n\nhttps://example.com/raw"));

        assertFalse(joined.contains("#"));
        assertFalse(joined.contains(">"));
        assertFalse(joined.contains("- "));
        assertFalse(joined.contains("https://"));
        assertTrue(joined.contains("标题"));
        assertTrue(joined.contains("引用"));
        assertTrue(joined.contains("列表项"));
        assertTrue(joined.contains("链接"));
    }
}
