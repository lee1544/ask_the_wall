package com.example.atw;

import com.example.atw.utils.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenUtilTest {

    private JwtTokenUtil jwtTokenUtil;

    @BeforeEach
    void setUp() {
        jwtTokenUtil = new JwtTokenUtil();
        ReflectionTestUtils.setField(jwtTokenUtil, "secret", "test-secret-key-for-jwt-token-generation");
        ReflectionTestUtils.setField(jwtTokenUtil, "expiration", 86400L);
    }

    @Test
    void testGenerateToken_Success() {
        String token = jwtTokenUtil.generateToken("testuser", 1, 1);

        assertNotNull(token);
        assertTrue(token.length() > 0);
    }

    @Test
    void testGetUsernameFromToken_Success() {
        String token = jwtTokenUtil.generateToken("testuser", 1, 1);

        String username = jwtTokenUtil.getUsernameFromToken(token);

        assertEquals("testuser", username);
    }

    @Test
    void testGetUserIdFromToken_Success() {
        String token = jwtTokenUtil.generateToken("testuser", 1, 1);

        Integer userId = jwtTokenUtil.getUserIdFromToken(token);

        assertEquals(1, userId);
    }

    @Test
    void testGetOrgIdFromToken_Success() {
        String token = jwtTokenUtil.generateToken("testuser", 1, 1);

        Integer orgId = jwtTokenUtil.getOrgIdFromToken(token);

        assertEquals(1, orgId);
    }

    @Test
    void testValidateToken_ValidToken() {
        String token = jwtTokenUtil.generateToken("testuser", 1, 1);

        assertTrue(jwtTokenUtil.validateToken(token));
    }

    @Test
    void testValidateToken_InvalidToken() {
        assertFalse(jwtTokenUtil.validateToken("invalid.token.here"));
    }

    @Test
    void testValidateToken_NullToken() {
        assertFalse(jwtTokenUtil.validateToken(null));
    }

    @Test
    void testValidateToken_TamperedToken() {
        String token = jwtTokenUtil.generateToken("testuser", 1, 1);
        String tamperedToken = token.substring(0, token.length() - 5) + "XXXXX";

        assertFalse(jwtTokenUtil.validateToken(tamperedToken));
    }
}
