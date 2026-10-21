package com.example.atw.web;

import com.example.atw.annotation.RequirePermission;
import com.example.atw.entity.Permission;
import com.example.atw.exception.BusinessException;
import com.example.atw.service.PermissionService;
import com.example.atw.service.UserRoleService;
import com.example.atw.utils.UserContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private UserRoleService userRoleService;

    @RequirePermission("role:view")
    @GetMapping
    public List<Permission> getPermissions() {
        return permissionService.getAllPermissions();
    }

    @RequirePermission("role:update")
    @PutMapping("/{id}/status")
    public Permission updatePermissionStatus(@PathVariable Integer id,
                                             @RequestParam Integer status) {
        if (!UserContextUtil.isDevBypass()
                && !userRoleService.isSuperAdmin(UserContextUtil.getCurrentUserId())) {
            throw new BusinessException("只有超级管理员可以修改权限状态");
        }
        return permissionService.updatePermissionStatus(id, status);
    }
}
