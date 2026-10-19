package com.example.atw.client.repository;

import com.example.atw.client.entity.ClientImageJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClientImageJobRepository extends JpaRepository<ClientImageJob, String> {

    Optional<ClientImageJob> findByIdAndUserId(String id, Integer userId);

    List<ClientImageJob> findByExpiresAtBefore(LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update ClientImageJob j set j.state = :state, j.message = :message, j.mediaId = :mediaId, "
            + "j.error = :error, j.code = :code, j.expiresAt = :expiresAt, j.updatedAt = :updatedAt "
            + "where j.id = :id and j.state in :expectedStates")
    int transition(@Param("id") String id,
                   @Param("expectedStates") Collection<String> expectedStates,
                   @Param("state") String state,
                   @Param("message") String message,
                   @Param("mediaId") String mediaId,
                   @Param("error") String error,
                   @Param("code") String code,
                   @Param("expiresAt") LocalDateTime expiresAt,
                   @Param("updatedAt") LocalDateTime updatedAt);
}
