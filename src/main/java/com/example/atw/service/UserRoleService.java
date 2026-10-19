package com.example.atw.service;

import com.example.atw.constant.SystemConstant;
import com.example.atw.entity.Role;
import com.example.atw.repository.RoleRepository;
import com.example.atw.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserRoleService {
    
    @Autowired
    private UserRoleRepository userRoleRepository;
    
    @Autowired
    private RoleRepository roleRepository;
    
    public List<Integer> getRoleIdsByUserId(Integer userId) {
        return userRoleRepository.findRoleIdsByUserId(userId);
    }
    
    public String getRoleCodeById(Integer roleId) {
        return roleRepository.findById(roleId)
                .map(Role::getRoleCode)
                .orElse("");
    }

    public List<String> getRoleCodesByUserId(Integer userId) {
        List<Integer> roleIds = getRoleIdsByUserId(userId);
        return roleIds.stream()
                .map(this::getRoleCodeById)
                .filter(code -> code != null && !code.isEmpty())
                .collect(Collectors.toList());
    }

    public boolean isOrgAdmin(Integer userId, Integer orgId) {
        List<Integer> roleIds = getRoleIdsByUserId(userId);
        for (Integer roleId : roleIds) {
            String roleCode = getRoleCodeById(roleId);
            if (SystemConstant.ORG_ADMIN_ROLE_CODE_PREFIX.concat(orgId.toString()).equals(roleCode)) {
                return true;
            }
        }
        return false;
    }

    public boolean isSuperAdmin(Integer userId) {
        List<String> userRoles = getRoleCodesByUserId(userId);
        return userRoles.contains(SystemConstant.SUPER_ADMIN_ROLE_CODE);
    }

    public UserRoleInfo getUserRoleInfo(Integer userId, Integer orgId) {
        List<String> roleCodes = getRoleCodesByUserId(userId);
        boolean isSuperAdmin = roleCodes.contains(SystemConstant.SUPER_ADMIN_ROLE_CODE);
        boolean isOrgAdmin = roleCodes.stream()
                .anyMatch(code -> SystemConstant.ORG_ADMIN_ROLE_CODE_PREFIX.concat(orgId.toString()).equals(code));
        return new UserRoleInfo(isSuperAdmin, isOrgAdmin);
    }

    public static class UserRoleInfo {
        private final boolean superAdmin;
        private final boolean orgAdmin;

        public UserRoleInfo(boolean superAdmin, boolean orgAdmin) {
            this.superAdmin = superAdmin;
            this.orgAdmin = orgAdmin;
        }

        public boolean isSuperAdmin() { return superAdmin; }
        public boolean isOrgAdmin() { return orgAdmin; }
    }
}
