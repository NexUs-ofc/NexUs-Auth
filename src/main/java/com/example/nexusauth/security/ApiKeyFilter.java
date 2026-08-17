package com.example.nexusauth.security;

import com.example.nexusauth.exception.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-KEY";

    private static final Logger logger =
            LoggerFactory.getLogger(ApiKeyFilter.class);

    private final String apiKey;

    public ApiKeyFilter(@Value("${app.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String path = request.getRequestURI();

        logger.info("URI: {} (Should Not Filter)", path);

        boolean ignorar =
                path.startsWith("/swagger-ui")
                        || path.startsWith("/v3/api-docs")
                        || path.startsWith("/actuator/prometheus")
                        || path.startsWith("/tracing")
                        || path.startsWith("/static")
                        || path.equals("/render/health");

        logger.info("Ignorar filtro: {}", ignorar);

        return ignorar;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        logger.info("URI={} (do Filter Internal)", request.getRequestURI());

        String requestApiKey = request.getHeader(API_KEY_HEADER);

        if (requestApiKey == null || !apiKey.equals(requestApiKey)) {
            logger.warn(
                    "Tentativa de acesso com API Key inválida: {}",
                    request.getRequestURI()
            );

            throw new UnauthorizedException("API Key inválida");
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                "api-key-client",
                null,
                Collections.emptyList()
        );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}