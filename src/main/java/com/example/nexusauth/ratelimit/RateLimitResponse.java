package com.example.nexusauth.ratelimit;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class RateLimitResponse {

    private RateLimitResponse() {
    }

    public static void write(HttpServletResponse response, long retryAfter) throws IOException {
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"code":"RATE_LIMIT_EXCEEDED","message":"Muitas requisicoes. Tente novamente mais tarde."}
                """);
    }
}
