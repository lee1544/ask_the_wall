package com.example.atw.service;

import com.example.atw.constant.SystemConstant;
import com.example.atw.entity.Permission;
import com.example.atw.entity.Role;
import com.example.atw.entity.UserRole;
import com.example.atw.exception.BusinessException;
import com.example.atw.exception.NotFoundException;
import com.example.atw.filter.UserPermissionCache;
import com.example.atw.repository.RoleRepository;
import com.example.atw.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoleService {
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private PermissionService permissionService;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private UserPermissionCache userPermissionCache;
    
    public List<Role> getRolesByOrgId(Integer orgId) {
        List<Role> roles = roleRepository.findByOrgId(orgId);
        roles.forEach(role -> role.setPermissions(permissionService.getPermissionsByRoleId(role.getId())));
        return roles;
    }
    
    public Role getRoleById(Integer id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("角色不存在"));
    }
    
    @Transactional
    public Role createRole(Integer orgId, Role role) {
        String roleCode = role.getRoleCode();
        
        if (roleRepository.existsByRoleCodeAndOrgId(roleCode, orgId)) {
            throw new BusinessException("角色编码已存在");
        }
        
        role.setOrgId(orgId);
        role.setStatus(1);
        role.setCreateTime(LocalDateTime.now());
        role.setUpdateTime(LocalDateTime.now());
        
        return roleRepository.save(role);
    }
    
    @Transactional
    public Role updateRole(Integer id, Role roleData) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("角色不存在"));
        
        role.setRoleName(roleData.getRoleName());
        role.setDescription(roleData.getDescription());
        role.setUpdateTime(LocalDateTime.now());
        
        return roleRepository.save(role);
    }
    
    @Transactional
    public void deleteRole(Integer id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("角色不存在"));
        
        if (SystemConstant.SUPER_ADMIN_ROLE_CODE.equals(role.getRoleCode())) {
            throw new BusinessException("超级管理员角色不能删除");
        }
        if (role.getRoleCode().startsWith(SystemConstant.ORG_ADMIN_ROLE_CODE_PREFIX)) {
            throw new BusinessException("组织管理员角色不能删除");
        }
        // 在删除角色前先获取拥有该角色的用户，用于后续清除缓存
        List<UserRole> affectedUserRoles = userRoleRepository.findByRoleId(id);
        roleRepository.deleteById(id);
        // clearRolePermissions 内部也会尝试清缓存，但 user_role 可能已被级联删除
        permissionService.clearRolePermissions(id);
        // 兜底：用删除前获取的用户记录确保缓存被清除
        affectedUserRoles.forEach(ur -> userPermissionCache.evict(ur.getUserId()));
    }
    
    @Transactional
    public void assignPermissionsToRole(Integer roleId, Integer[] permissionIds) {
        roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("角色不存在"));
        // PermissionService 内部已处理缓存清除
        permissionService.assignPermissionsToRole(roleId, permissionIds);
    }
    
    public List<Permission> getRolePermissions(Integer roleId) {
        roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("角色不存在"));
        return permissionService.getPermissionsByRoleId(roleId);
    }

    public Role getRoleByCode(String roleCode) {
        return roleRepository.findByRoleCode(roleCode).orElse(null);
    }

    public List<Integer> getUserIdsByRoleId(Integer roleId) {
        return userRoleRepository.findByRoleId(roleId).stream()
                .map(UserRole::getUserId)
                .collect(Collectors.toList());
    }
}
