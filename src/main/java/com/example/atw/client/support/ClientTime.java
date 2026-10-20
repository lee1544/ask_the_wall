package com.example.atw.client.support;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 统一使用北京时间存储与输出，API 时间为 ISO-8601（带 +08:00）。
 */
public final class ClientTime {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private ClientTime() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    public static LocalDateTime expireAfterHours(int hours) {
        return now().plusHours(hours);
    }

    public static String iso(LocalDateTime time) {
        return time == null ? null : time.atZone(ZONE).toOffsetDateTime().toString();
    }

    public static boolean isExpired(LocalDateTime time) {
        return time != null && time.isBefore(now());
    }
}
