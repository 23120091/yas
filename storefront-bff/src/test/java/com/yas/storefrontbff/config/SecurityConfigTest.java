package com.yas.storefrontbff.config;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        ReactiveClientRegistrationRepository clientRegistrationRepository =
            mock(ReactiveClientRegistrationRepository.class);
        securityConfig = new SecurityConfig(clientRegistrationRepository);
    }

    @Test
    void generateAuthoritiesFromClaim_WhenRolesExist_ShouldAddRolePrefix() {
        Collection<GrantedAuthority> authorities = securityConfig.generateAuthoritiesFromClaim(List.of("ADMIN", "CUSTOMER"));

        assertEquals(2, authorities.size());
        assertTrue(authorities.stream().anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())));
        assertTrue(authorities.stream().anyMatch(authority -> "ROLE_CUSTOMER".equals(authority.getAuthority())));
    }

    @Test
    void userAuthoritiesMapperForKeycloak_WhenOauth2HasRealmRoles_ShouldMapAuthorities() {
        Map<String, Object> attributes = Map.of(
            "realm_access", Map.of("roles", List.of("MANAGER", "STAFF")));
        OAuth2UserAuthority oauth2UserAuthority = new OAuth2UserAuthority(attributes);
        GrantedAuthoritiesMapper mapper = securityConfig.userAuthoritiesMapperForKeycloak();

        Set<String> mappedAuthorities = mapper.mapAuthorities(Set.of(oauth2UserAuthority))
            .stream()
            .map(GrantedAuthority::getAuthority)
            .collect(java.util.stream.Collectors.toSet());

        assertEquals(Set.of("ROLE_MANAGER", "ROLE_STAFF"), mappedAuthorities);
    }

    @Test
    void userAuthoritiesMapperForKeycloak_WhenOidcHasRealmRoles_ShouldMapAuthorities() {
        OidcIdToken idToken = new OidcIdToken(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("sub", "user-1"));
        OidcUserInfo userInfo = new OidcUserInfo(Map.of(
            "realm_access", Map.of("roles", List.of("CUSTOMER"))));
        OidcUserAuthority oidcUserAuthority = new OidcUserAuthority(idToken, userInfo);
        GrantedAuthoritiesMapper mapper = securityConfig.userAuthoritiesMapperForKeycloak();

        Collection<? extends GrantedAuthority> mappedAuthorities = mapper.mapAuthorities(Set.of(oidcUserAuthority));

        assertEquals(1, mappedAuthorities.size());
        assertTrue(mappedAuthorities.stream().anyMatch(authority -> "ROLE_CUSTOMER".equals(authority.getAuthority())));
    }

    @Test
    void userAuthoritiesMapperForKeycloak_WhenRealmAccessMissing_ShouldReturnEmptyAuthorities() {
        OAuth2UserAuthority oauth2UserAuthority = new OAuth2UserAuthority(Map.of("sub", "user-1"));
        GrantedAuthoritiesMapper mapper = securityConfig.userAuthoritiesMapperForKeycloak();

        Collection<? extends GrantedAuthority> mappedAuthorities = mapper.mapAuthorities(Set.of(oauth2UserAuthority));

        assertTrue(mappedAuthorities.isEmpty());
    }
}
