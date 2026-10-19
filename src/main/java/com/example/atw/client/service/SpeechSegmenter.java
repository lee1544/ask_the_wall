package com.example.atw.client.service;

import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * speech-segments-v1：Markdown 清理与固定长度分段。
 */
@Component
public class SpeechSegmenter {

    public static final String VERSION = "speech-segments-v1";

    private static final Pattern CODE_BLOCK = Pattern.compile("(?s)```.*?```");
    private static final Pattern TILDE_CODE_BLOCK = Pattern.compile("(?s)~~~.*?~~~");
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`]*`");
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\([^)]*\\)");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile(
            "^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?\\s*$");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)\\]\\(([^)]*)\\)");
    private static final Pattern BARE_URL = Pattern.compile("(?i)https?://\\S+");
    private static final Pattern MAILTO = Pattern.compile("(?i)mailto:\\S+");
    private static final Pattern SENTENCE = Pattern.compile("[^。！？!?\\n]+[。！？!?\\n]*|[。！？!?\\n]+");

    public List<String> segment(String markdown) {
        String cleaned = clean(markdown);
        if (cleaned.isEmpty()) {
            throw new ClientApiException(422, ClientCodes.NO_READABLE_TEXT, "没有可朗读的文字");
        }
        List<String> sentences = splitSentences(cleaned);
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentUnits = 0;
        boolean first = true;

        for (String sentence : sentences) {
            int index = 0;
            while (index < sentence.length()) {
                int codePoint = sentence.codePointAt(index);
                int units = Character.charCount(codePoint);
                int limit = first ? 120 : 500;
                if (currentUnits + units > limit && current.length() > 0) {
                    segments.add(current.toString());
                    current.setLength(0);
                    currentUnits = 0;
                    first = false;
                }
                current.appendCodePoint(codePoint);
                currentUnits += units;
                index += units;
            }
            int threshold = first ? 40 : 350;
            if (currentUnits >= threshold && current.length() > 0) {
                segments.add(current.toString());
                current.setLength(0);
                currentUnits = 0;
                first = false;
            }
        }
        if (current.length() > 0) {
            segments.add(current.toString());
        }
        if (segments.isEmpty()) {
            throw new ClientApiException(422, ClientCodes.NO_READABLE_TEXT, "没有可朗读的文字");
        }
        return segments;
    }

    private String clean(String markdown) {
        if (markdown == null) {
            return "";
        }
        String text = CODE_BLOCK.matcher(markdown).replaceAll(" ");
        text = TILDE_CODE_BLOCK.matcher(text).replaceAll(" ");
        text = INLINE_CODE.matcher(text).replaceAll(" ");
        text = IMAGE.matcher(text).replaceAll(" ");
        text = HTML_TAG.matcher(text).replaceAll(" ");

        String[] lines = text.split("\\n", -1);
        StringBuilder rebuilt = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches("^([-*_])(\\s*\\1){2,}\\s*$")) {
                continue;
            }
            if (TABLE_SEPARATOR.matcher(line).matches()) {
                continue;
            }
            if (line.indexOf('|') >= 0) {
                String[] cells = line.split("\\|");
                List<String> parts = new ArrayList<>();
                for (String cell : cells) {
                    String value = cell.trim();
                    if (!value.isEmpty()) {
                        parts.add(value);
                    }
                }
                if (!parts.isEmpty()) {
                    rebuilt.append(String.join("，", parts)).append('\n');
                    continue;
                }
            }
            String withoutMarker = line.replaceFirst("^\\s*(>\\s?)+", "");
            withoutMarker = withoutMarker.replaceFirst("^\\s*#{1,6}\\s+", "");
            withoutMarker = withoutMarker.replaceFirst("^\\s*([-*+]|\\d+[.)])\\s+", "");
            rebuilt.append(withoutMarker).append('\n');
        }
        text = rebuilt.toString();

        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        text = text.replaceAll("__(.+?)__", "$1");
        text = text.replaceAll("~~(.+?)~~", "$1");
        text = text.replaceAll("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", "$1");

        Matcher linkMatcher = LINK.matcher(text);
        StringBuffer linkBuffer = new StringBuffer();
        while (linkMatcher.find()) {
            String label = linkMatcher.group(1).trim();
            if (label.isEmpty() || label.matches("(?i)^(https?://|mailto:|www\\.).*")) {
                linkMatcher.appendReplacement(linkBuffer, " ");
            } else {
                linkMatcher.appendReplacement(linkBuffer, Matcher.quoteReplacement(label));
            }
        }
        linkMatcher.appendTail(linkBuffer);
        text = linkBuffer.toString();

        text = BARE_URL.matcher(text).replaceAll(" ");
        text = MAILTO.matcher(text).replaceAll(" ");
        text = text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }

    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE.matcher(text);
        while (matcher.find()) {
            String value = matcher.group();
            if (value != null && !value.isEmpty()) {
                sentences.add(value);
            }
        }
        return sentences;
    }
}
