package com.example.atw.client.repository;

import com.example.atw.client.entity.ClientRequestRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ClientRequestRecordRepository extends JpaRepository<ClientRequestRecord, Long> {

    Optional<ClientRequestRecord> findByUserIdAndRequestId(Integer userId, String requestId);

    List<ClientRequestRecord> findByUserIdAndGenerationId(Integer userId, String generationId);

    @Modifying
    @Query("delete from ClientRequestRecord r where r.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
