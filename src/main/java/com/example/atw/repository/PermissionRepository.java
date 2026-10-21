package com.example.atw.repository;

import com.example.atw.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Integer> {
    
    @Query("SELECT p FROM Permission p JOIN RolePermission rp ON p.id = rp.permissionId WHERE rp.roleId IN :roleIds AND p.status = 1")
    List<Permission> findByRoleIds(@Param("roleIds") List<Integer> roleIds);

    @Query("SELECT p FROM Permission p JOIN RolePermission rp ON p.id = rp.permissionId WHERE rp.roleId = :roleId")
    List<Permission> findAllByRoleId(@Param("roleId") Integer roleId);
    
    List<Permission> findByStatus(Integer status);

    List<Permission> findAllByOrderByPermissionCodeAsc();

    Optional<Permission> findByPermissionCode(String permissionCode);
}
