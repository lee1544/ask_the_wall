package com.example.atw.service;

import com.example.atw.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthServiceTest {

    @Test
    void userInfoWithoutAuthenticatedUserIsUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> new AuthService().getUserInfo(null));
    }
}
