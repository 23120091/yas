package com.yas.storefrontbff.controller;

import com.yas.storefrontbff.viewmodel.AuthenticationInfoVm;
import com.yas.storefrontbff.viewmodel.AuthenticatedUserVm; 

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.user.OAuth2User;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationControllerTest {

    @Mock
    private OAuth2User principal;

    @InjectMocks
    private AuthenticationController authenticationController;

    @Test
    void user_WhenLoggedIn_ShouldReturnTrueAndUsername() {
        String username = "customer_123";
        when(principal.getAttribute("preferred_username")).thenReturn(username);

        ResponseEntity<AuthenticationInfoVm> response = authenticationController.user(principal);

        assertNotNull(response.getBody());
        assertTrue(response.getBody().isAuthenticated());
        // Chỗ này phải gọi .authenticatedUser() để lấy object bên trong
        assertEquals(username, response.getBody().authenticatedUser().username());
    }

    @Test
    void user_WhenNotLoggedIn_ShouldReturnFalseAndNullUser() {
        ResponseEntity<AuthenticationInfoVm> response = authenticationController.user(null);

        assertNotNull(response.getBody());
        assertFalse(response.getBody().isAuthenticated());
        assertNull(response.getBody().authenticatedUser());
    }
}