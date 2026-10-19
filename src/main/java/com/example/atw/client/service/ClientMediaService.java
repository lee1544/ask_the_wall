package com.example.atw.client.service;

import com.example.atw.client.config.ClientModelProperties;
import com.example.atw.client.entity.ClientMedia;
import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;
import com.example.atw.client.repository.ClientMediaRepository;
import com.example.atw.client.support.ClientTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClientMediaService {

    private static final Logger log = LoggerFactory.getLogger(ClientMediaService.class);

    public static final String TYPE_AUDIO = "audio";
    public static final String TYPE_IMAGE = "image";

    @Autowired
    private ClientMediaRepository repository;

    @Autowired
    private ClientModelProperties properties;

    private Path root;

    @PostConstruct
    public void init() {
        root = Paths.get(properties.getMedia().getDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建媒体目录: " + root, e);
        }
    }

    public ClientMedia save(Integer userId, String type, String contentType, byte[] data, String cacheKey) {
        String id = UUID.randomUUID().toString().replace("-", "");
        String extension = TYPE_IMAGE.equals(type) ? ".png" : ".wav";
        Path dir = root.resolve(String.valueOf(userId));
        Path target = dir.resolve(id + extension);
        try {
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, id, ".tmp");
            Files.write(tmp, data);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFailure) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ClientApiException(500, ClientCodes.INTERNAL_ERROR, "媒体文件保存失败");
        }

        ClientMedia media = new ClientMedia();
        media.setId(id);
        media.setUserId(userId);
        media.setType(type);
        media.setContentType(contentType);
        media.setPath(target.toString());
        media.setSize((long) data.length);
        media.setCacheKey(cacheKey);
        media.setCreatedAt(ClientTime.now());
        media.setExpiresAt(ClientTime.expireAfterHours(properties.getMedia().getTtlHours()));
        repository.save(media);
        return media;
    }

    public Optional<ClientMedia> findCached(Integer userId, String cacheKey) {
        if (cacheKey == null || cacheKey.isEmpty()) {
            return Optional.empty();
        }
        Optional<ClientMedia> cached = repository
                .findFirstByUserIdAndCacheKeyAndExpiresAtAfterOrderByCreatedAtDesc(userId, cacheKey, ClientTime.now());
        if (!cached.isPresent()) {
            return Optional.empty();
        }
        if (!Files.isRegularFile(Paths.get(cached.get().getPath()))) {
            return Optional.empty();
        }
        return cached;
    }

    public MediaFile open(Integer userId, String id) {
        ClientMedia media = repository.findByIdAndUserId(id, userId).orElse(null);
        if (media == null) {
            throw new ClientApiException(404, ClientCodes.RESOURCE_NOT_FOUND, "资源不存在");
        }
        if (ClientTime.isExpired(media.getExpiresAt())) {
            throw new ClientApiException(410, ClientCodes.RESOURCE_EXPIRED, "临时资源已过期。");
        }
        Path path = Paths.get(media.getPath());
        if (!Files.isRegularFile(path)) {
            throw new ClientApiException(410, ClientCodes.RESOURCE_EXPIRED, "临时资源已过期。");
        }
        return new MediaFile(media, path);
    }

    public String url(String id) {
        return "/api/v1/client/media/" + id;
    }

    @Transactional
    public void purgeExpired() {
        List<ClientMedia> expired = repository.findByExpiresAtBefore(ClientTime.now());
        for (ClientMedia media : expired) {
            try {
                Files.deleteIfExists(Paths.get(media.getPath()));
            } catch (IOException e) {
                log.warn("清理媒体文件失败: {}", media.getPath(), e);
            }
            repository.delete(media);
        }
    }

    public static class MediaFile {
        private final ClientMedia media;
        private final Path path;

        public MediaFile(ClientMedia media, Path path) {
            this.media = media;
            this.path = path;
        }

        public ClientMedia getMedia() {
            return media;
        }

        public Path getPath() {
            return path;
        }

        public long size() {
            try {
                return Files.size(path);
            } catch (IOException e) {
                return media.getSize() == null ? 0L : media.getSize();
            }
        }
    }
}
