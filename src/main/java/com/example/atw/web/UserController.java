package com.example.atw.web;

import com.example.atw.annotation.RequirePermission;
import com.example.atw.dto.CreateUserRequest;
import com.example.atw.dto.PageResponse;
import com.example.atw.dto.UpdatePasswordRequest;
import com.example.atw.entity.Role;
import com.example.atw.entity.User;
import com.example.atw.service.RoleService;
import com.example.atw.service.UserService;
import com.example.atw.utils.UserContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
            @RequestParam(name = "realname", required = false) String realname) {
        Integer orgId = UserContextUtil.getCurrentOrgId();
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<User> userPage = userService.getUsersByOrgId(orgId, realname, pageRequest);

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
        User user = userService.getUsersById(id);
        return user;
    }

    @RequirePermission("user:update")
    @PutMapping("/{id}/status")
    public User updateUserStatus(@PathVariable Integer id, @RequestParam Integer status) {
        return userService.updateUserStatus(id, status);
    }

    @RequirePermission("user:update")
    @PutMapping("/{id}/password")
    public User updateUser(@PathVariable Integer id, @Valid @RequestBody UpdatePasswordRequest request) {
        return userService.updateUserPassword(id, request.getPassword());
    }

    @RequirePermission("user:reset-password")
    @PutMapping("/{id}/reset-password")
    public User resetUserPassword(@PathVariable Integer id) {
        return userService.resetUserPassword(id);
    }

    @RequirePermission("user:delete")
    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Integer id) {
        userService.deleteUser(id);
    }

    @RequirePermission("user:assign-role")
    @PostMapping("/{id}/roles")
    public void assignRolesToUser(@PathVariable Integer id, @RequestBody Integer[] roleIds) {
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
}
