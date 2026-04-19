package com.yas.storefrontbff.controller;

import com.yas.storefrontbff.viewmodel.AuthenticationInfoVm;
import com.yas.storefrontbff.viewmodel.AuthenticatedUserVm; 
import com.yas.storefrontbff.viewmodel.CartDetailVm;
import com.yas.storefrontbff.viewmodel.CartGetDetailVm;
import com.yas.storefrontbff.viewmodel.CartItemVm;
import com.yas.storefrontbff.viewmodel.GuestUserVm;
import com.yas.storefrontbff.viewmodel.TokenResponseVm;

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

    @Test
    void testRemainingViewModels() {
        // 1. Test CartDetailVm và CartGetDetailVm
        CartDetailVm detail = new CartDetailVm(1L, 101L, 2);
        CartGetDetailVm cartGet = new CartGetDetailVm(10L, "user_id", List.of(detail));
        
        assertEquals(101L, detail.productId());
        assertEquals("user_id", cartGet.customerId());
        assertFalse(cartGet.cartDetails().isEmpty());

        // 2. Test CartItemVm (Có chứa logic static method)
        CartItemVm item = CartItemVm.fromCartDetailVm(detail);
        assertEquals(101L, item.productId());
        assertEquals(2, item.quantity());

        // 3. Test GuestUserVm và TokenResponseVm
        GuestUserVm guest = new GuestUserVm("G1", "guest@example.com", "secret");
        TokenResponseVm token = new TokenResponseVm("access_token", "refresh_token");

        assertEquals("guest@example.com", guest.email());
        assertEquals("access_token", token.accessToken());
    }
}