package com.example.atw.service;

import com.example.atw.entity.Permission;
import com.example.atw.entity.RolePermission;
import com.example.atw.exception.BusinessException;
import com.example.atw.filter.UserPermissionCache;
import com.example.atw.repository.PermissionRepository;
import com.example.atw.repository.RolePermissionRepository;
import com.example.atw.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private UserPermissionCache userPermissionCache;

    public List<String> getPermissionCodesByRoleIds(List<Integer> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Permission> permissions = permissionRepository.findByRoleIds(roleIds);
        return permissions.stream()
                .map(Permission::getPermissionCode)
                .distinct()
                .collect(Collectors.toList());
    }

    @Transactional
    public void assignAllPermissionsToRole(Integer roleId) {
        List<Permission> allPermissions = permissionRepository.findByStatus(1);

        List<RolePermission> rolePermissions = allPermissions.stream()
                .map(permission -> {
                    RolePermission rp = new RolePermission();
                    rp.setRoleId(roleId);
                    rp.setPermissionId(permission.getId());
                    return rp;
                })
                .collect(Collectors.toList());

        rolePermissionRepository.saveAll(rolePermissions);
        evictCacheByRoleId(roleId);
    }

    @Transactional
    public void assignPermissionsByCodesToRole(Integer roleId, String[] permissionCodes) {
        rolePermissionRepository.deleteByRoleId(roleId);
        for (String code : permissionCodes) {
            Permission permission = permissionRepository.findByPermissionCode(code)
                    .orElseThrow(() -> new BusinessException("权限不存在: " + code));
            RolePermission rolePermission = new RolePermission();
            rolePermission.setRoleId(roleId);
            rolePermission.setPermissionId(permission.getId());
            rolePermissionRepository.save(rolePermission);
        }
        evictCacheByRoleId(roleId);
    }

    @Transactional
    public void assignPermissionsToRole(Integer roleId, Integer[] permissionIds) {
        rolePermissionRepository.deleteByRoleId(roleId);
        if (permissionIds != null && permissionIds.length > 0) {
            List<RolePermission> rolePermissions = Arrays.stream(permissionIds)
                    .map(permissionId -> {
                        RolePermission rolePermission = new RolePermission();
                        rolePermission.setRoleId(roleId);
                        rolePermission.setPermissionId(permissionId);
                        return rolePermission;
                    })
                    .collect(Collectors.toList());
            rolePermissionRepository.saveAll(rolePermissions);
        }
        evictCacheByRoleId(roleId);
    }

    public List<Permission> getAllActivePermissions() {
        return permissionRepository.findByStatus(1);
    }

    public List<Permission> getAllPermissions() {
        return permissionRepository.findAllByOrderByPermissionCodeAsc();
    }

    @Transactional
    public Permission updatePermissionStatus(Integer permissionId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("权限状态只能是 0 或 1");
        }
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new BusinessException("权限不存在"));
        permission.setStatus(status);
        permission.setUpdateTime(LocalDateTime.now());
        Permission saved = permissionRepository.save(permission);
        userPermissionCache.evictAll();
        return saved;
    }

    public List<Permission> getPermissionsByRoleId(Integer roleId) {
        return permissionRepository.findAllByRoleId(roleId);
    }

    @Transactional
    public void clearRolePermissions(Integer roleId) {
        // 先清缓存（此时 user_role 记录还存在），再删 role_permission
        evictCacheByRoleId(roleId);
        rolePermissionRepository.deleteByRoleId(roleId);
    }

    /**
     * 根据角色ID清除拥有该角色的所有用户的权限缓存
     */
    private void evictCacheByRoleId(Integer roleId) {
        userRoleRepository.findByRoleId(roleId)
                .forEach(ur -> userPermissionCache.evict(ur.getUserId()));
    }
}
