package com.example.atw.filter;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 用户权限缓存组件：key=userId，value=权限列表
 * TTL 5分钟，最多缓存1000个用户，避免每次请求都查询数据库
 */
@Component
public class UserPermissionCache {

    private final Cache<Integer, List<String>> cache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    public List<String> get(Integer userId) {
        return cache.getIfPresent(userId);
    }

    public void put(Integer userId, List<String> permissions) {
        cache.put(userId, permissions);
    }

    /**
     * 清除指定用户的权限缓存
     */
    public void evict(Integer userId) {
        cache.invalidate(userId);
    }

    /**
     * 清除所有用户的权限缓存
     */
    public void evictAll() {
        cache.invalidateAll();
    }
}
