package com.example.atw.service;

import com.example.atw.dto.LoginRequest;
import com.example.atw.dto.LoginResponse;
import com.example.atw.dto.UserInfo;
import com.example.atw.entity.User;
import com.example.atw.exception.NotFoundException;
import com.example.atw.exception.UnauthorizedException;
import com.example.atw.repository.UserRepository;
import com.example.atw.utils.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserRoleService userRoleService;
    
    @Autowired
    private PermissionService permissionService;
    
    @Autowired
    private OrganizationService organizationService;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UnauthorizedException("用户名或密码错误"));
        
        if (user.getStatus() == 0) {
            throw new UnauthorizedException("账号已被禁用");
        }
        
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        //判断用户是否第一次登录
        boolean isFirstLogin = user.getLastLoginTime() == null;
        user.setLastLoginTime(LocalDateTime.now());
        userRepository.save(user);
        
        String token = jwtTokenUtil.generateToken(user.getUsername(), user.getId(), user.getOrgId());
        
        UserInfo userInfo = buildUserInfo(user, isFirstLogin);
        
        return new LoginResponse(token, userInfo);
    }
    
    public UserInfo getUserInfo(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));
        boolean isFirstLogin = user.getLastLoginTime() == null;
        return buildUserInfo(user, isFirstLogin);
    }
    
    private UserInfo buildUserInfo(User user, boolean isFirstLogin) {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setRealName(user.getRealName());
        userInfo.setOrgId(user.getOrgId());
        userInfo.setFirstLogin(isFirstLogin);
        String orgName = organizationService.getOrgNameById(user.getOrgId());
        userInfo.setOrgName(orgName);
        
        List<Integer> roleIds = userRoleService.getRoleIdsByUserId(user.getId());
        List<String> roleCodes = roleIds.stream()
                .map(roleId -> userRoleService.getRoleCodeById(roleId))
                .collect(Collectors.toList());
        userInfo.setRoles(roleCodes);
        
        List<String> permissions = permissionService.getPermissionCodesByRoleIds(roleIds);
        userInfo.setPermissions(permissions);
        
        return userInfo;
    }
}
