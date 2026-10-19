package com.example.atw.filter;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @author Li Jian on 2026/6/28.
 */
@Component
public class JwtTokenBlacklist {
    private final Cache<String, Boolean> blacklist = CacheBuilder.newBuilder()
            .expireAfterWrite(7, TimeUnit.DAYS).maximumSize(10000)
            .build();

    public void invalidate(String token) {
        blacklist.put(token, true);
    }

    public boolean isInvalid(String token) {
        return blacklist.getIfPresent(token) != null;
    }
}