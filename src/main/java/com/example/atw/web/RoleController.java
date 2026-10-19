package com.example.atw.web;

import com.example.atw.annotation.RequirePermission;
import com.example.atw.entity.Permission;
import com.example.atw.entity.Role;
import com.example.atw.service.RoleService;
import com.example.atw.utils.UserContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
public class RoleController {
    
    @Autowired
    private RoleService roleService;
    
    @RequirePermission("role:view")
    @GetMapping
    public List<Role> getRoles() {
        Integer orgId = UserContextUtil.getCurrentOrgId();
        return roleService.getRolesByOrgId(orgId);
    }
    
    @RequirePermission("role:view")
    @GetMapping("/{id}")
    public Role getRole(@PathVariable Integer id) {
        return roleService.getRoleById(id);
    }
    
    @RequirePermission("role:create")
    @PostMapping
    public Role createRole(@RequestBody Role role) {
        Integer orgId = UserContextUtil.getCurrentOrgId();
        return roleService.createRole(orgId, role);
    }
    
    @RequirePermission("role:update")
    @PutMapping("/{id}")
    public Role updateRole(@PathVariable Integer id, @RequestBody Role role) {
        return roleService.updateRole(id, role);
    }
    
    @RequirePermission("role:delete")
    @DeleteMapping("/{id}")
    public void deleteRole(@PathVariable Integer id) {
        roleService.deleteRole(id);
    }
    
    @RequirePermission("role:assign-permission")
    @PostMapping("/{id}/permissions")
    public void assignPermissionsToRole(@PathVariable Integer id, @RequestBody Integer[] permissionIds) {
        roleService.assignPermissionsToRole(id, permissionIds);
    }
    
    @RequirePermission("role:view")
    @GetMapping("/{id}/permissions")
    public List<Permission> getRolePermissions(@PathVariable Integer id) {
        return roleService.getRolePermissions(id);
    }
}
