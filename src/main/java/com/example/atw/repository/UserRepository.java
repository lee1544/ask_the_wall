package com.example.atw.repository;

import com.example.atw.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    @Query("SELECT u FROM User u WHERE u.orgId = :orgId " +
            "AND (:realname IS NULL OR u.realName LIKE CONCAT('%', :realname, '%')) " +
            "AND (:status IS NULL OR u.status = :status)")
    Page<User> findByOrgIdAndRealname(@Param("orgId") Integer orgId,
                                      @Param("realname") String realname,
                                      @Param("status") Integer status,
                                      Pageable pageable);

    List<User> findByOrgId(Integer orgId);

    @Query("SELECT COUNT(u) FROM User u WHERE u.orgId = :orgId")
    long countByOrgId(@Param("orgId") Integer orgId);
}