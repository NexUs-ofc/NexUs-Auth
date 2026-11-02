package com.example.nexusauth.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import java.io.IOException;

public final class RateLimitFilter extends OncePerRequestFilter {

    private final AuthRateLimitService limits;

    public RateLimitFilter(AuthRateLimitService limits) {
        this.limits = limits;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return "OPTIONS".equals(request.getMethod()) || !path.startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        try {
            limits.checkRequest(request, "/api/auth/token/refresh".equals(path));
        } catch (RateLimitExceededException exception) {
            RateLimitResponse.write(response, exception.retryAfter());
            return;
        }
        chain.doFilter(request, response);
    }
}
