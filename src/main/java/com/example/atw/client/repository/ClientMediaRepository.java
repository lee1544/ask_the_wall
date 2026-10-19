package com.example.atw.client.repository;

import com.example.atw.client.entity.ClientMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ClientMediaRepository extends JpaRepository<ClientMedia, String> {

    Optional<ClientMedia> findByIdAndUserId(String id, Integer userId);

    Optional<ClientMedia> findFirstByUserIdAndCacheKeyAndExpiresAtAfterOrderByCreatedAtDesc(
            Integer userId, String cacheKey, LocalDateTime now);

    List<ClientMedia> findByExpiresAtBefore(LocalDateTime now);
}
