package com.example.atw.client.support;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 统一使用 UTC 存储与输出，API 时间为 ISO-8601（带 Z）。
 */
public final class ClientTime {

    private ClientTime() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public static LocalDateTime expireAfterHours(int hours) {
        return now().plusHours(hours);
    }

    public static String iso(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC).toString();
    }

    public static boolean isExpired(LocalDateTime time) {
        return time != null && time.isBefore(now());
    }
}
