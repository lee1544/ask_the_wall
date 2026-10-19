package com.example.atw;

import com.example.atw.dto.LoginRequest;
import com.example.atw.dto.LoginResponse;
import com.example.atw.dto.UserInfo;
import com.example.atw.entity.User;
import com.example.atw.exception.UnauthorizedException;
import com.example.atw.repository.UserRepository;
import com.example.atw.service.AuthService;
import com.example.atw.service.OrganizationService;
import com.example.atw.service.PermissionService;
import com.example.atw.service.UserRoleService;
import com.example.atw.utils.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleService userRoleService;

    @Mock
    private PermissionService permissionService;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenUtil jwtTokenUtil;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1);
        testUser.setUsername("testuser");
        testUser.setPassword("$2a$12$encodedPassword");
        testUser.setRealName("测试用户");
        testUser.setOrgId(1);
        testUser.setStatus(1);
        testUser.setLastLoginTime(LocalDateTime.now().minusDays(1));

        loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password123");
    }

    @Test
    void testLogin_Success() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", testUser.getPassword())).thenReturn(true);
        when(jwtTokenUtil.generateToken(anyString(), anyInt(), anyInt())).thenReturn("mock-token");
        when(organizationService.getOrgNameById(1)).thenReturn("测试组织");
        when(userRoleService.getRoleIdsByUserId(1)).thenReturn(java.util.Arrays.asList(1));
        when(userRoleService.getRoleCodeById(1)).thenReturn("USER");
        when(permissionService.getPermissionCodesByRoleIds(anyList())).thenReturn(java.util.Arrays.asList("user:view"));

        LoginResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("mock-token", response.getToken());
        assertNotNull(response.getUserInfo());
        assertEquals("testuser", response.getUserInfo().getUsername());
        verify(userRepository).save(testUser);
    }

    @Test
    void testLogin_UserNotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> {
            authService.login(loginRequest);
        });
    }

    @Test
    void testLogin_WrongPassword() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", testUser.getPassword())).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> {
            authService.login(loginRequest);
        });
    }

    @Test
    void testLogin_UserDisabled() {
        testUser.setStatus(0);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        assertThrows(UnauthorizedException.class, () -> {
            authService.login(loginRequest);
        });
    }

    @Test
    void testGetUserInfo_Success() {
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(organizationService.getOrgNameById(1)).thenReturn("测试组织");
        when(userRoleService.getRoleIdsByUserId(1)).thenReturn(java.util.Arrays.asList(1));
        when(userRoleService.getRoleCodeById(1)).thenReturn("USER");
        when(permissionService.getPermissionCodesByRoleIds(anyList())).thenReturn(java.util.Arrays.asList("user:view"));

        UserInfo userInfo = authService.getUserInfo(1);

        assertNotNull(userInfo);
        assertEquals("testuser", userInfo.getUsername());
        assertEquals("测试组织", userInfo.getOrgName());
    }
}
