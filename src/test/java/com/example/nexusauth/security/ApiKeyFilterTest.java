package com.example.nexusauth.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.List;

class ApiKeyFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void skipsCorsPreflightRequests() {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/google/authenticate");
        request.addHeader("Origin", "https://client.example.com");
        request.addHeader("Access-Control-Request-Method", "POST");

        ApiKeyFilter filter = new ApiKeyFilter("api-key");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void validApiKeyDoesNotAuthenticateAUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/logout");
        request.addHeader("X-API-KEY", "api-key");
        MockFilterChain chain = new MockFilterChain();

        new ApiKeyFilter("api-key").doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void preservesAnExistingJwtAuthentication() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject("42").build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/logout");
        request.addHeader("X-API-KEY", "api-key");

        new ApiKeyFilter("api-key").doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(authentication);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"wrong-key", " "})
    void rejectsInvalidApiKeysWithoutContinuingTheChain(String apiKey) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login/password");
        if (apiKey != null) {
            request.addHeader("X-API-KEY", apiKey);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new ApiKeyFilter("api-key").doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
