package com.yas.backofficebff.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthenticationController.class)
class AuthenticationControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Nên trả về username yas_admin khi giả lập đăng nhập qua Keycloak")
    void user_WhenAuthenticated_ShouldReturnAuthenticatedUser() throws Exception {
        String mockAdminName = "yas_admin";

        mockMvc.perform(get("/authentication/user")
                // Giả lập User đã login thành công vào Keycloak (OIDC)
                .with(oidcLogin()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        .attributes(attrs -> attrs.put("preferred_username", mockAdminName))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(mockAdminName));
    }

    @Test
    @DisplayName("Nên trả về 401 Unauthorized khi truy cập mà không có token")
    void user_WhenUnauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/authentication/user"))
                .andExpect(status().isUnauthorized());
    } 
}
