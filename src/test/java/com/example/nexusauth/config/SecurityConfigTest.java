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
    void allowsCorsForEveryOriginMethodAndHeader() {
        CorsConfigurationSource source = new SecurityConfig().corsConfigurationSource();
        HttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/google/authenticate");

        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).isEqualTo(List.of("*"));
        assertThat(configuration.getAllowedMethods()).isEqualTo(List.of("*"));
        assertThat(configuration.getAllowedHeaders()).isEqualTo(List.of("*"));
        assertThat(configuration.getExposedHeaders()).isEqualTo(List.of("*"));
    }
}
