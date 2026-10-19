package com.example.atw.client.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 生成组取消标记与在途上游连接登记。
 */
@Component
public class ChatCancellationRegistry {

    private final Cache<String, Boolean> stopped = CacheBuilder.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(100000)
            .build();

    private final ConcurrentHashMap<String, Set<HttpURLConnection>> active = new ConcurrentHashMap<>();

    public void markStopped(String generationId) {
        if (generationId != null) {
            stopped.put(generationId, Boolean.TRUE);
        }
    }

    public boolean isStopped(String generationId) {
        return generationId != null && stopped.getIfPresent(generationId) != null;
    }

    public void register(String generationId, HttpURLConnection connection) {
        if (generationId == null || connection == null) {
            return;
        }
        active.computeIfAbsent(generationId, key -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(connection);
    }

    public void unregister(String generationId, HttpURLConnection connection) {
        if (generationId == null || connection == null) {
            return;
        }
        Set<HttpURLConnection> connections = active.get(generationId);
        if (connections != null) {
            connections.remove(connection);
            if (connections.isEmpty()) {
                active.remove(generationId, connections);
            }
        }
    }

    public void cancel(String generationId) {
        markStopped(generationId);
        Set<HttpURLConnection> connections = active.get(generationId);
        if (connections == null) {
            return;
        }
        for (HttpURLConnection connection : connections) {
            try {
                connection.disconnect();
            } catch (Exception ignored) {
            }
        }
    }
}
