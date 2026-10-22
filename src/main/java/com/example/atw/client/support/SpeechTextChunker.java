package com.example.atw.client.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SpeechTextChunker {

    private SpeechTextChunker() {
    }

    public static List<String> chunk(String text, int maxChars) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        if (maxChars <= 0 || text.length() <= maxChars) {
            return Collections.singletonList(text);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        int length = text.length();
        while (start < length) {
            int end = Math.min(length, start + maxChars);
            if (end < length) {
                int minimum = Math.max(start + 1, start + maxChars / 2);
                for (int index = end - 1; index >= minimum; index--) {
                    if (isBreak(text.charAt(index))) {
                        end = index + 1;
                        break;
                    }
                }
            }
            if (end < length && end > start
                    && Character.isHighSurrogate(text.charAt(end - 1))
                    && Character.isLowSurrogate(text.charAt(end))) {
                end++;
            }
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
    }

    private static boolean isBreak(char value) {
        return value == '\n'
                || value == '\r'
                || value == ' '
                || value == '\t'
                || value == '\u3002'
                || value == '\uFF01'
                || value == '\uFF1F'
                || value == '!'
                || value == '?'
                || value == '\uFF0C'
                || value == '\u3001'
                || value == '\uFF1B'
                || value == ';'
                || value == '\uFF1A'
                || value == ':';
    }
}
