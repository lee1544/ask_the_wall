package com.example.atw.web;

import com.example.atw.annotation.RequirePermission;
import com.example.atw.dto.CreateUserRequest;
import com.example.atw.dto.PageResponse;
import com.example.atw.dto.UpdatePasswordRequest;
import com.example.atw.dto.UpdateUserRequest;
import com.example.atw.entity.Role;
import com.example.atw.entity.User;
import com.example.atw.exception.NotFoundException;
import com.example.atw.service.RoleService;
import com.example.atw.service.UserRoleService;
import com.example.atw.service.UserService;
import com.example.atw.utils.UserContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserRoleService userRoleService;

    @RequirePermission("user:create")
    @PostMapping
    public User createUser(@Valid @RequestBody CreateUserRequest createUserRequest) {
        Integer orgId = UserContextUtil.getCurrentOrgId();
        return userService.createUser(orgId, createUserRequest);
    }

    @RequirePermission("user:view")
    @GetMapping
    public PageResponse<User> getUsers(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "realname", required = false) String realname,
            @RequestParam(name = "status", required = false) Integer status) {
        Integer orgId = UserContextUtil.getCurrentOrgId();
        Sort sort = Sort.by(Sort.Order.desc("createTime"), Sort.Order.desc("id"));
        PageRequest pageRequest = PageRequest.of(page, size, sort);
        Page<User> userPage = userService.getUsersByOrgId(orgId, trimToNull(realname), status, pageRequest);

        return PageResponse.of(
                userPage.getContent(),
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements()
        );
    }

    @RequirePermission("user:view")
    @GetMapping("/{id}")
    public User getUserById(@PathVariable Integer id) {
        return requireCurrentOrgUser(id);
    }

    @RequirePermission("user:view")
    @GetMapping("/{id}/roles")
    public List<Integer> getUserRoles(@PathVariable Integer id) {
        requireCurrentOrgUser(id);
        return userRoleService.getRoleIdsByUserId(id);
    }

    @RequirePermission("user:update")
    @PutMapping("/{id}")
    public User updateUser(@PathVariable Integer id, @Valid @RequestBody UpdateUserRequest request) {
        requireCurrentOrgUser(id);
        return userService.updateUserRealName(id, request.getRealName());
    }

    @RequirePermission("user:update")
    @PutMapping("/{id}/status")
    public User updateUserStatus(@PathVariable Integer id, @RequestParam Integer status) {
        requireCurrentOrgUser(id);
        return userService.updateUserStatus(id, status);
    }

    @RequirePermission("user:update")
    @PutMapping("/{id}/password")
    public User updateUser(@PathVariable Integer id, @Valid @RequestBody UpdatePasswordRequest request) {
        requireCurrentOrgUser(id);
        return userService.updateUserPassword(id, request.getPassword());
    }

    @RequirePermission("user:reset-password")
    @PutMapping("/{id}/reset-password")
    public User resetUserPassword(@PathVariable Integer id) {
        requireCurrentOrgUser(id);
        return userService.resetUserPassword(id);
    }

    @RequirePermission("user:delete")
    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Integer id) {
        requireCurrentOrgUser(id);
        userService.deleteUser(id);
    }

    @RequirePermission("user:assign-role")
    @PostMapping("/{id}/roles")
    public void assignRolesToUser(@PathVariable Integer id, @RequestBody Integer[] roleIds) {
        requireCurrentOrgUser(id);
        Integer orgId = UserContextUtil.getCurrentOrgId();
        userService.assignRolesToUser(id, roleIds, orgId);
    }

    /**
     * 按角色编码查询用户列表
     * 例如：ORG_ADMIN_1, NORMAL_ADMIN_1, SUPER_ADMIN
     */
    @GetMapping("/by_role_code")
    public List<User> getUsersByRoleCode(@RequestParam String roleCode) {
        Role role = roleService.getRoleByCode(roleCode);
        if (role == null) {
            return Collections.emptyList();
        }
        List<Integer> userIds = roleService.getUserIdsByRoleId(role.getId());
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }
        return userIds.stream()
                .filter(userId -> !userId.equals(UserContextUtil.getCurrentUserId()))
                .map(userId -> {
                    try {
                        return userService.getUsersById(userId);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(u -> u != null)
                .collect(Collectors.toList());
    }

    private User requireCurrentOrgUser(Integer userId) {
        User user = userService.getUsersById(userId);
        if (!UserContextUtil.getCurrentOrgId().equals(user.getOrgId())) {
            throw new NotFoundException("用户不存在");
        }
        return user;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
