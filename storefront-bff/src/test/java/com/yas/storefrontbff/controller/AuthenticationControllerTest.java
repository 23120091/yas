package com.yas.storefrontbff.controller;

import com.yas.storefrontbff.viewmodel.AuthenticatedUser;
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
        // Giả lập có user
        String username = "customer_123";
        when(principal.getAttribute("preferred_username")).thenReturn(username);

        ResponseEntity<AuthenticationInfoVm> response = authenticationController.user(principal);

        assertTrue(response.getBody().isAuthenticated()); // Check field isAuthenticated
        assertEquals(username, response.getBody().authenticatedUser().username());
    }

    @Test
    void user_WhenNotLoggedIn_ShouldReturnFalseAndNullUser() {
        // Trường hợp principal là null (chưa đăng nhập)
        ResponseEntity<AuthenticationInfoVm> response = authenticationController.user(null);

        assertFalse(response.getBody().isAuthenticated());
        assertNull(response.getBody().authenticatedUser());
    }
}