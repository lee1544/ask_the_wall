package com.example.atw.service;

import com.example.atw.constant.SystemConstant;
import com.example.atw.dto.CreateUserRequest;
import com.example.atw.entity.Role;
import com.example.atw.entity.User;
import com.example.atw.entity.UserRole;
import com.example.atw.exception.BusinessException;
import com.example.atw.exception.NotFoundException;
import com.example.atw.exception.UnauthorizedException;
import com.example.atw.filter.UserPermissionCache;
import com.example.atw.repository.RoleRepository;
import com.example.atw.repository.UserRepository;
import com.example.atw.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private UserPermissionCache userPermissionCache;

    @Transactional
    public User createUser(Integer currentOrgId, CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        //组织管理员新增用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setOrgId(currentOrgId);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        return userRepository.save(user);
    }

    public Page<User> getUsersByOrgId(Integer orgId, String realName, Integer status,
                                      Pageable pageable, boolean includeRoles) {
        Page<User> userPage = userRepository.findByOrgIdAndRealname(orgId, realName, status, pageable);
        String orgName = organizationService.getOrgNameById(orgId);
        userPage.forEach(user -> user.setOrgName(orgName));
        if (includeRoles) {
            fillRoleSummaries(userPage.getContent());
        }
        return userPage;
    }

    private void fillRoleSummaries(List<User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }

        List<Integer> userIds = new ArrayList<>();
        for (User user : users) {
            userIds.add(user.getId());
        }
        List<UserRole> links = userRoleRepository.findByUserIdIn(userIds);
        if (links.isEmpty()) {
            users.forEach(user -> {
                user.setRoleIds(Collections.emptyList());
                user.setRoleNames(Collections.emptyList());
            });
            return;
        }

        List<Integer> roleIds = new ArrayList<>();
        for (UserRole link : links) {
            roleIds.add(link.getRoleId());
        }
        Map<Integer, String> roleNameMap = new HashMap<>();
        for (Role role : roleRepository.findAllById(roleIds)) {
            roleNameMap.put(role.getId(), role.getRoleName());
        }

        Map<Integer, List<Integer>> roleIdsByUser = new HashMap<>();
        Map<Integer, List<String>> roleNamesByUser = new HashMap<>();
        for (UserRole link : links) {
            roleIdsByUser.computeIfAbsent(link.getUserId(), key -> new ArrayList<>())
                    .add(link.getRoleId());
            String roleName = roleNameMap.get(link.getRoleId());
            if (roleName != null) {
                roleNamesByUser.computeIfAbsent(link.getUserId(), key -> new ArrayList<>())
                        .add(roleName);
            }
        }

        for (User user : users) {
            user.setRoleIds(roleIdsByUser.getOrDefault(user.getId(), Collections.emptyList()));
            user.setRoleNames(roleNamesByUser.getOrDefault(user.getId(), Collections.emptyList()));
        }
    }

    public User getUsersById(Integer userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("用户不存在"));
        String orgName = organizationService.getOrgNameById(user.getOrgId());
        user.setOrgName(orgName);
        return user;
    }

    /**
     * 轻量查询：只获取用户真实姓名，不查组织信息
     */
    public String getUserRealName(Integer userId) {
        return userRepository.findById(userId)
                .map(User::getRealName)
                .orElse(null);
    }

    // ==================== 用户姓名缓存查询 ====================

    private static final int MAX_NAME_CACHE = 100;
    private final Map<Integer, String> userNameCache = new ConcurrentHashMap<>();

    /**
     * 获取用户显示姓名（带本地缓存，避免重复查库）
     * <p>
     * 与 {@link #getUserRealName} 的区别：
     * <ul>
     *   <li>内部缓存，相同 userId 不会重复查库</li>
     *   <li>userId 为 null 时返回 "未知"</li>
     *   <li>用户不存在时返回 "用户(id)" 而非 null</li>
     * </ul>
     */
    public String getUserName(Integer userId) {
        if (userId == null) return "未知";
        // 防止缓存无限增长：超过上限时清空。必须在 computeIfAbsent 之外操作，
        // 否则会在其 mapping function 内修改同一个 map，违反 ConcurrentHashMap 约束。
        if (userNameCache.size() >= MAX_NAME_CACHE) {
            userNameCache.clear();
        }
        return userNameCache.computeIfAbsent(userId, id -> {
            try {
                String name = getUserRealName(id);
                return name != null ? name : "用户(" + id + ")";
            } catch (Exception e) {
                return "用户(" + id + ")";
            }
        });
    }

    public Map<Integer, String> getRealNamesByIds(List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }
        List<User> users = userRepository.findAllById(userIds);
        Map<Integer, String> realNameMap = new HashMap<>();
        for (User user : users) {
            realNameMap.put(user.getId(), user.getRealName());
        }
        return realNameMap;
    }

    @Transactional
    public User updateUserRealName(Integer userId, String realName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));

        user.setRealName(realName);
        user.setUpdateTime(LocalDateTime.now());
        return userRepository.save(user);
    }

    @Transactional
    public User updateUserStatus(Integer userId, Integer status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));

        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());

        return userRepository.save(user);
    }

    @Transactional
    public User updateUserPassword(Integer userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));

        user.setPassword(passwordEncoder.encode(password));
        user.setUpdateTime(LocalDateTime.now());
        return userRepository.save(user);
    }

    @Transactional
    public User resetUserPassword(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));

        // 重置密码为系统初始密码12345678
        user.setPassword(passwordEncoder.encode(SystemConstant.DEFAULT_SYSTEM_PASSWORD));
        // 清空最后登录时间，使下次登录时触发 firstLogin=true，提示修改密码
        user.setLastLoginTime(null);
        user.setUpdateTime(LocalDateTime.now());
        userRepository.save(user);

        // 清除权限缓存
        userPermissionCache.evict(userId);

        return user;
    }

    @Transactional
    public void deleteUser(Integer userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("用户不存在");
        }
        userRoleRepository.deleteByUserId(userId);
        userRepository.deleteById(userId);
        userPermissionCache.evict(userId);
    }

    @Transactional
    public void assignRolesToUser(Integer userId, Integer[] roleIds, Integer currentOrgId) {
        userRoleRepository.deleteByUserId(userId);
        if (roleIds != null) {
            for (Integer roleId : roleIds) {
                Role role = roleRepository.findById(roleId)
                        .orElseThrow(() -> new NotFoundException("角色不存在"));
                if (!role.getOrgId().equals(currentOrgId)) {
                    throw new UnauthorizedException("角色不属于当前组织");
                }
                UserRole userRole = new UserRole();
                userRole.setUserId(userId);
                userRole.setRoleId(roleId);
                userRoleRepository.save(userRole);
            }
        }
        // 角色变更后清除该用户的权限缓存
        userPermissionCache.evict(userId);
    }
}
