package com.example.atw;

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
import com.example.atw.service.OrganizationService;
import com.example.atw.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private UserPermissionCache userPermissionCache;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private CreateUserRequest createRequest;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1);
        testUser.setUsername("newuser");
        testUser.setPassword("encodedPassword");
        testUser.setRealName("新用户");
        testUser.setOrgId(1);
        testUser.setStatus(1);

        createRequest = new CreateUserRequest();
        createRequest.setUsername("newuser");
        createRequest.setPassword("password123");
        createRequest.setRealName("新用户");
    }

    // ==================== createUser ====================

    @Test
    void testCreateUser_Success() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        // 模拟 JPA save 的回填 ID 行为
        when(userRepository.save(any())).thenAnswer(invocation -> {
            User userArg = invocation.getArgument(0);
            userArg.setId(1);
            userArg.setCreateTime(LocalDateTime.now());
            userArg.setUpdateTime(LocalDateTime.now());
            return userArg;
        });

        User result = userService.createUser(1, createRequest);

        assertNotNull(result);
        assertEquals("newuser", result.getUsername());
        assertEquals(1, result.getOrgId().intValue());
        assertEquals(1, result.getStatus().intValue());
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void testCreateUser_UsernameExists() {
        when(userRepository.existsByUsername("newuser")).thenReturn(true);

        assertThrows(BusinessException.class, () ->
                userService.createUser(1, createRequest));

        verify(userRepository, never()).save(any());
        verify(userRoleRepository, never()).save(any());
    }

    // ==================== getUsersByOrgId ====================

    @Test
    void testGetUsersByOrgId_Success() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        User user = new User();
        user.setId(1);
        user.setOrgId(1);
        List<User> users = Collections.singletonList(user);
        Page<User> userPage = new PageImpl<>(users, pageRequest, 1);

        when(userRepository.findByOrgIdAndRealname(eq(1), anyString(), isNull(), eq(pageRequest))).thenReturn(userPage);
        when(organizationService.getOrgNameById(1)).thenReturn("测试组织");

        Page<User> result = userService.getUsersByOrgId(1, "", null, pageRequest, true);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("测试组织", result.getContent().get(0).getOrgName());
    }

    @Test
    void testGetUsersByOrgId_EmptyResult() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<User> emptyPage = Page.empty(pageRequest);

        when(userRepository.findByOrgIdAndRealname(eq(999), anyString(), isNull(), eq(pageRequest))).thenReturn(emptyPage);

        Page<User> result = userService.getUsersByOrgId(999, "", null, pageRequest, true);

        assertNotNull(result);
        assertTrue(result.getContent().isEmpty());
    }

    // ==================== getUsersById ====================

    @Test
    void testGetUsersById_Success() {
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(organizationService.getOrgNameById(1)).thenReturn("测试组织");

        User result = userService.getUsersById(1);

        assertNotNull(result);
        assertEquals("newuser", result.getUsername());
        assertEquals("测试组织", result.getOrgName());
    }

    @Test
    void testGetUsersById_NotFound() {
        when(userRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                userService.getUsersById(999));
    }

    // ==================== getRealNamesByIds ====================

    @Test
    void testGetRealNamesByIds_Success() {
        User user1 = new User();
        user1.setId(1);
        user1.setRealName("用户A");
        User user2 = new User();
        user2.setId(2);
        user2.setRealName("用户B");

        when(userRepository.findAllById(Arrays.asList(1, 2))).thenReturn(Arrays.asList(user1, user2));

        Map<Integer, String> result = userService.getRealNamesByIds(Arrays.asList(1, 2));

        assertEquals(2, result.size());
        assertEquals("用户A", result.get(1));
        assertEquals("用户B", result.get(2));
    }

    @Test
    void testGetRealNamesByIds_NullInput() {
        Map<Integer, String> result = userService.getRealNamesByIds(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(userRepository, never()).findAllById(any());
    }

    @Test
    void testGetRealNamesByIds_EmptyInput() {
        Map<Integer, String> result = userService.getRealNamesByIds(Collections.emptyList());

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(userRepository, never()).findAllById(any());
    }

    // ==================== updateUserStatus ====================

    @Test
    void testUpdateUserStatus_Success() {
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User result = userService.updateUserStatus(1, 0);

        assertNotNull(result);
        assertEquals(0, result.getStatus());
        verify(userRepository).save(testUser);
    }

    @Test
    void testUpdateUserStatus_NotFound() {
        when(userRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                userService.updateUserStatus(999, 0));

        verify(userRepository, never()).save(any());
    }

    // ==================== updateUserPassword ====================

    @Test
    void testUpdateUserPassword_Success() {
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("newPassword")).thenReturn("newEncodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User result = userService.updateUserPassword(1, "newPassword");

        assertNotNull(result);
        verify(userRepository).save(testUser);
    }

    @Test
    void testUpdateUserPassword_NotFound() {
        when(userRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                userService.updateUserPassword(999, "newPassword"));

        verify(userRepository, never()).save(any());
    }

    // ==================== deleteUser ====================

    @Test
    void testDeleteUser_Success() {
        when(userRepository.existsById(1)).thenReturn(true);

        userService.deleteUser(1);

        verify(userRoleRepository).deleteByUserId(1);
        verify(userRepository).deleteById(1);
        verify(userPermissionCache).evict(1);
    }

    @Test
    void testDeleteUser_NotFound() {
        when(userRepository.existsById(999)).thenReturn(false);

        assertThrows(NotFoundException.class, () ->
                userService.deleteUser(999));

        verify(userRoleRepository, never()).deleteByUserId(anyInt());
        verify(userRepository, never()).deleteById(anyInt());
        verify(userPermissionCache, never()).evict(anyInt());
    }

    // ==================== assignRolesToUser ====================

    @Test
    void testAssignRolesToUser_Success() {
        Role role1 = new Role();
        role1.setId(1);
        role1.setOrgId(1);
        Role role2 = new Role();
        role2.setId(3);
        role2.setOrgId(1);

        Integer[] roleIds = {1, 3};

        when(roleRepository.findById(1)).thenReturn(Optional.of(role1));
        when(roleRepository.findById(3)).thenReturn(Optional.of(role2));

        userService.assignRolesToUser(1, roleIds, 1);

        verify(userRoleRepository).deleteByUserId(1);
        verify(roleRepository, times(2)).findById(anyInt());
        verify(userRoleRepository, times(2)).save(any(UserRole.class));
        verify(userPermissionCache).evict(1);
    }

    @Test
    void testAssignRolesToUser_RoleNotFound() {
        Integer[] roleIds = {999};

        when(roleRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                userService.assignRolesToUser(1, roleIds, 1));

        verify(userRoleRepository).deleteByUserId(1);
        verify(userRoleRepository, never()).save(any(UserRole.class));
    }

    @Test
    void testAssignRolesToUser_RoleNotInOrg() {
        Role role = new Role();
        role.setId(1);
        role.setOrgId(2); // different org

        Integer[] roleIds = {1};

        when(roleRepository.findById(1)).thenReturn(Optional.of(role));

        assertThrows(UnauthorizedException.class, () ->
                userService.assignRolesToUser(1, roleIds, 1));

        verify(userRoleRepository).deleteByUserId(1);
        verify(userRoleRepository, never()).save(any(UserRole.class));
    }
}