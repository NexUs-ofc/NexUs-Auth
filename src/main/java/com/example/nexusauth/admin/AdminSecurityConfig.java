package com.example.nexusauth.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Cadeia propria para o painel do administrador: so a chave do painel vale aqui, nem API key do
 * aplicativo nem JWT de usuario.
 */
@Configuration
@EnableScheduling
public class AdminSecurityConfig {

    @Bean
    @Order(0)
    SecurityFilterChain adminSecurityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            @Value("${app.admin.key:}") String chave) throws Exception {
        return http.securityMatcher("/admin/**", "/actuator/prometheus")
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new AdminKeyFilter(chave), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
