package com.example.nexusauth.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protege as rotas do painel do administrador com uma chave unica, a mesma em todos os servicos do NexUs.
 *
 * <p>A chave vai no header {@code X-Admin-Key}. Em {@code /actuator/**} tambem vale o parametro {@code key},
 * para um Prometheus externo conseguir raspar as metricas sem header customizado.
 */
public class AdminKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Admin-Key";

    private static final String PARAMETRO = "key";

    private final byte[] chave;

    public AdminKeyFilter(String chave) {
        this.chave = chave == null || chave.isBlank() ? null : chave.trim().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (CorsUtils.isPreFlightRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (chave == null) {
            responderErro(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Chave do painel nao configurada no servidor (ADMIN_API_KEY).");
            return;
        }

        String enviada = request.getHeader(HEADER);
        if ((enviada == null || enviada.isBlank()) && request.getRequestURI().startsWith("/actuator/")) {
            enviada = request.getParameter(PARAMETRO);
        }

        if (enviada == null
                || !MessageDigest.isEqual(chave, enviada.trim().getBytes(StandardCharsets.UTF_8))) {
            responderErro(response, HttpServletResponse.SC_UNAUTHORIZED, "Chave do painel invalida ou ausente.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void responderErro(HttpServletResponse response, int status, String mensagem) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + mensagem + "\"}");
    }
}
