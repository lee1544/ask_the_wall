package com.example.atw.repository;

import com.example.atw.entity.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Integer> {
    
    Optional<Organization> findByOrgCode(String orgCode);
    
    boolean existsByOrgCode(String orgCode);
    
    boolean existsByOrgName(String orgName);

    Page<Organization> findAll(Pageable pageable);
}
