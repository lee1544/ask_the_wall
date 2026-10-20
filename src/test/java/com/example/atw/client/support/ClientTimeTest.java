package com.example.atw.client.support;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientTimeTest {

    @Test
    void formatsClientTimestampsInBeijingTime() {
        LocalDateTime value = LocalDateTime.of(2026, 10, 20, 18, 0);

        assertEquals("2026-10-20T18:00+08:00", ClientTime.iso(value));
    }

    @Test
    void nowUsesBeijingTime() {
        LocalDateTime expected = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));

        assertTrue(Duration.between(expected, ClientTime.now()).abs().getSeconds() <= 2);
    }

    @Test
    void expirationComparesBeijingTime() {
        assertTrue(ClientTime.isExpired(ClientTime.now().minusMinutes(1)));
        assertFalse(ClientTime.isExpired(ClientTime.now().plusMinutes(1)));
    }
}
