package com.example.atw.client.service;

import com.example.atw.client.error.ClientApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientIdentityServiceTest {

    private final ClientIdentityService service = new ClientIdentityService();

    @Test
    void usesAnonymousOwnerWhenJwtIsAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertEquals(ClientIdentityService.ANONYMOUS_USER_ID, service.requireOwnerId(request));
    }

    @Test
    void usesJwtUserWhenPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", 7);

        assertEquals(7, service.requireOwnerId(request));
    }

    @Test
    void documentEmbeddingsRequireAuthenticatedUser() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThrows(ClientApiException.class, () -> service.requireAuthenticatedUserId(request));
    }
}
