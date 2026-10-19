package com.example.atw.repository;

import com.example.atw.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Integer> {
    
    List<RolePermission> findByRoleId(Integer roleId);
    
    void deleteByRoleId(Integer roleId);
}
