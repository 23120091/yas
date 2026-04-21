package com.yas.backofficebff.controller;

import com.yas.backofficebff.viewmodel.AuthenticatedUser;
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
    private OAuth2User principal; // Giả lập đối tượng User

    @InjectMocks
    private AuthenticationController authenticationController;

    @Test
    void user_WhenPrincipalExists_ShouldReturnCorrectUsername() {
        // 1. Giả lập giá trị trả về khi code gọi principal.getAttribute
        String expectedUsername = "yas_test_user";
        when(principal.getAttribute("preferred_username")).thenReturn(expectedUsername);

        // 2. Gọi trực tiếp hàm trong Controller (không qua MockMvc/HTTP)
        ResponseEntity<AuthenticatedUser> response = authenticationController.user(principal);

        // 3. Kiểm tra kết quả
        assertNotNull(response.getBody());
        assertEquals(expectedUsername, response.getBody().username());
        
        // Xác nhận hàm getAttribute đã được gọi đúng 1 lần
        verify(principal, times(1)).getAttribute("preferred_username");
    }

    @Test
    void user_WhenPreferredUsernameIsNull_ShouldReturnNullUsername() {
        // Giả lập trường hợp User đăng nhập nhưng không có thuộc tính preferred_username
        when(principal.getAttribute("preferred_username")).thenReturn(null);

        ResponseEntity<AuthenticatedUser> response = authenticationController.user(principal);

        assertNotNull(response.getBody());
        assertNull(response.getBody().username());
    }

    @Test
    void user_WhenPreferredUsernameIsEmpty_ShouldReturnEmptyUsername() {
        when(principal.getAttribute("preferred_username")).thenReturn("");

        ResponseEntity<AuthenticatedUser> response = authenticationController.user(principal);

        assertEquals("", response.getBody().username());
    }
}