package com.example.nexusauth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;


@Component
public class TracingAuthFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(TracingAuthFilter.class);
    private static final String PARAM_NAME = "key";
    private static final String PROTECTED_PATH = "/actuator/prometheus";

    @Value("${app.tracing-access-key:}")
    private String tracingAccessKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PROTECTED_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (tracingAccessKey == null || tracingAccessKey.isBlank()) {
            logger.warn("TRACING_ACESS não configurada — bloqueando acesso a {}", PROTECTED_PATH);
            responderErro(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "TRACING_ACESS não configurada no servidor.");
            return;
        }

        String chaveEnviada = request.getParameter(PARAM_NAME);

        if (chaveEnviada == null || !chavesIguais(chaveEnviada, tracingAccessKey)) {
            responderErro(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Acesso não autorizado. Chave TRACING_ACESS inválida ou ausente.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean chavesIguais(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void responderErro(HttpServletResponse response, int status, String mensagem) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + mensagem + "\"}");
    }
}