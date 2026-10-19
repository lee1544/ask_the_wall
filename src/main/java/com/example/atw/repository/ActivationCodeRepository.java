package com.example.atw.repository;

import com.example.atw.entity.ActivationCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface ActivationCodeRepository extends JpaRepository<ActivationCode, Integer> {

    Optional<ActivationCode> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ActivationCode a WHERE a.code = :code")
    Optional<ActivationCode> findByCodeForUpdate(@Param("code") String code);

    Optional<ActivationCode> findByLicenseId(String licenseId);

    boolean existsByCode(String code);

    Page<ActivationCode> findByStatus(Integer status, Pageable pageable);

    Page<ActivationCode> findByBatch(String batch, Pageable pageable);

    Page<ActivationCode> findByStatusAndBatch(Integer status, String batch, Pageable pageable);
}
