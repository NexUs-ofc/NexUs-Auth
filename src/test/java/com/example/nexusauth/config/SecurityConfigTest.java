package com.example.nexusauth.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigTest {

    @Test
    void allowsOnlyConfiguredOriginsAndPreservesMethodsAndHeaders() {
        CorsConfigurationSource source = new SecurityConfig().corsConfigurationSource(
                " https://frontend.example.com, http://localhost:3000, , ");
        HttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/google/authenticate");

        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly(
                "https://frontend.example.com", "http://localhost:3000");
        assertThat(configuration.checkOrigin("https://frontend.example.com"))
                .isEqualTo("https://frontend.example.com");
        assertThat(configuration.checkOrigin("https://other.example.com")).isNull();
        assertThat(configuration.getAllowedMethods()).isEqualTo(List.of("*"));
        assertThat(configuration.getAllowedHeaders()).isEqualTo(List.of("*"));
        assertThat(configuration.getExposedHeaders()).isEqualTo(List.of("*"));
        assertThat(configuration.getMaxAge()).isEqualTo(3600L);
    }

    @Test
    void doesNotAllowAnyOriginWhenUnset() {
        CorsConfigurationSource source = new SecurityConfig().corsConfigurationSource("");
        HttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login/password");

        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).isEmpty();
        assertThat(configuration.checkOrigin("https://frontend.example.com")).isNull();
    }
}
