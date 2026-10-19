package com.example.atw.service;

import com.example.atw.constant.SystemConstant;
import com.example.atw.dto.CreateOrganizationRequest;
import com.example.atw.dto.UpdateOrganizationRequest;
import com.example.atw.entity.Organization;
import com.example.atw.entity.Role;
import com.example.atw.entity.User;
import com.example.atw.entity.UserRole;
import com.example.atw.exception.BusinessException;
import com.example.atw.exception.NotFoundException;
import com.example.atw.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrganizationService {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Transactional
    public Organization createOrganization(CreateOrganizationRequest request) {
        if (organizationRepository.existsByOrgCode(request.getOrgCode())) {
            throw new BusinessException("组织编码已存在");
        }

        if (organizationRepository.existsByOrgName(request.getOrgName())) {
            throw new BusinessException("组织名称已存在");
        }

        Organization org = new Organization();
        org.setOrgCode(request.getOrgCode());
        org.setOrgName(request.getOrgName());
        org.setDescription(request.getDescription());
        org.setStatus(1);
        org.setCreateTime(LocalDateTime.now());
        org.setUpdateTime(LocalDateTime.now());

        organizationRepository.save(org);

        createOrgAdminRole(org.getId());

        createNormalUserRole(org.getId());

        createOrgAdminUser(org.getId(), request);

        return org;
    }

    private void createOrgAdminRole(Integer orgId) {
        Role orgAdminRole = new Role();
        orgAdminRole.setRoleCode(SystemConstant.ORG_ADMIN_ROLE_CODE_PREFIX + orgId);
        orgAdminRole.setRoleName("组织管理员");
        orgAdminRole.setDescription("管理本组织下的所有数据和用户");
        orgAdminRole.setOrgId(orgId);
        orgAdminRole.setStatus(1);
        orgAdminRole.setCreateTime(LocalDateTime.now());
        orgAdminRole.setUpdateTime(LocalDateTime.now());
        roleRepository.save(orgAdminRole);
        permissionService.assignPermissionsByCodesToRole(orgAdminRole.getId(), SystemConstant.ORG_ADMIN_PERMISSIONS);
    }

    private void createNormalUserRole(Integer orgId) {
        Role normalUserRole = new Role();
        normalUserRole.setRoleCode(SystemConstant.NORMAL_ADMIN_ROLE_CODE_PREFIX + orgId);
        normalUserRole.setRoleName("普通管理员");
        normalUserRole.setDescription("只能操作自己上传的数据和修改个人信息");
        normalUserRole.setOrgId(orgId);
        normalUserRole.setStatus(1);
        normalUserRole.setCreateTime(LocalDateTime.now());
        normalUserRole.setUpdateTime(LocalDateTime.now());
        roleRepository.save(normalUserRole);
        permissionService.assignPermissionsByCodesToRole(normalUserRole.getId(), SystemConstant.NORMAL_ADMIN_PERMISSIONS);
    }

    private void createOrgAdminUser(Integer orgId, CreateOrganizationRequest request) {
        User adminUser = new User();
        adminUser.setUsername(request.getAdminUsername());
        adminUser.setPassword(passwordEncoder.encode(request.getAdminPassword()));
        adminUser.setRealName(request.getAdminRealName());
        adminUser.setOrgId(orgId);
        adminUser.setStatus(1);
        adminUser.setCreateTime(LocalDateTime.now());
        adminUser.setUpdateTime(LocalDateTime.now());
        userRepository.save(adminUser);

        Role orgAdminRole = roleRepository.findByRoleCode(SystemConstant.ORG_ADMIN_ROLE_CODE_PREFIX + orgId)
                .orElseThrow(() -> new NotFoundException("组织管理员角色不存在"));

        UserRole userRole = new UserRole();
        userRole.setUserId(adminUser.getId());
        userRole.setRoleId(orgAdminRole.getId());
        userRoleRepository.save(userRole);
    }

    public String getOrgNameById(Integer orgId) {
        return organizationRepository.findById(orgId)
                .map(Organization::getOrgName)
                .orElse("未知组织");
    }

    public Organization getOrganizationById(Integer orgId) {
        return organizationRepository.findById(orgId)
                .orElseThrow(() -> new NotFoundException("组织不存在"));
    }

    public Page<Organization> getOrganizations(Pageable pageable) {
        return organizationRepository.findAll(pageable);
    }

    @Transactional
    public Organization updateOrganization(Integer id, UpdateOrganizationRequest request) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("组织不存在"));

        if (request.getOrgCode() != null) {
            if (!request.getOrgCode().equals(org.getOrgCode())
                    && organizationRepository.existsByOrgCode(request.getOrgCode())) {
                throw new BusinessException("组织编码已存在");
            }
            org.setOrgCode(request.getOrgCode());
        }

        if (request.getOrgName() != null) {
            if (!request.getOrgName().equals(org.getOrgName())
                    && organizationRepository.existsByOrgName(request.getOrgName())) {
                throw new BusinessException("组织名称已存在");
            }
            org.setOrgName(request.getOrgName());
        }

        if (request.getDescription() != null) {
            org.setDescription(request.getDescription());
        }

        if (request.getStatus() != null) {
            org.setStatus(request.getStatus());
        }

        org.setUpdateTime(LocalDateTime.now());
        return organizationRepository.save(org);
    }

    @Transactional
    public void deleteOrganization(Integer id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("组织不存在"));

        List<User> users = userRepository.findByOrgId(id);
        for (User user : users) {
            userRoleRepository.deleteByUserId(user.getId());
        }
        userRepository.deleteAll(users);

        List<Role> roles = roleRepository.findByOrgId(id);
        for (Role role : roles) {
            rolePermissionRepository.deleteByRoleId(role.getId());
        }
        roleRepository.deleteAll(roles);

        organizationRepository.delete(org);
    }
}
