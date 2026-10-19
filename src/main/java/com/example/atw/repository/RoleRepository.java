package com.example.atw.repository;

import com.example.atw.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {
    
    Optional<Role> findByRoleCode(String roleCode);
    
    List<Role> findByOrgId(Integer orgId);
    
    boolean existsByRoleCodeAndOrgId(String roleCode, Integer orgId);
}
