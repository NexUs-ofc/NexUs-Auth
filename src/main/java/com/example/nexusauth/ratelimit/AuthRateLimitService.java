package com.example.nexusauth.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

public final class AuthRateLimitService {

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private final InMemoryRateLimiter limiter;
    private final RateLimitProperties properties;
    private final ClientIpResolver clientIpResolver;

    public AuthRateLimitService(InMemoryRateLimiter limiter, RateLimitProperties properties,
                                ClientIpResolver clientIpResolver) {
        this.limiter = limiter;
        this.properties = properties;
        this.clientIpResolver = clientIpResolver;
    }

    public void checkRequest(HttpServletRequest request, boolean refresh) {
        String ip = clientIpResolver.resolve(request);
        var general = new InMemoryRateLimiter.Quota("ip:" + ip, properties.requestsPerMinute(), MINUTE);
        requireAvailable(refresh
                ? limiter.consume(general, new InMemoryRateLimiter.Quota(
                        "refresh:" + ip, properties.refreshPerMinute(), MINUTE))
                : limiter.consume(general));
    }

    public void checkLogin(String email, HttpServletRequest request) {
        requireAvailable(limiter.consume(new InMemoryRateLimiter.Quota(
                "login:" + clientIpResolver.resolve(request) + ":" + emailKey(email),
                properties.loginAttempts(), properties.loginWindow())));
    }

    public void checkOtpSend(String email) {
        String identity = emailKey(email);
        requireAvailable(limiter.consume(
                new InMemoryRateLimiter.Quota("otp:minute:" + identity, properties.otpPerMinute(), MINUTE),
                new InMemoryRateLimiter.Quota("otp:quarter:" + identity,
                        properties.otpPerQuarterHour(), Duration.ofMinutes(15))));
    }

    private void requireAvailable(long retryAfter) {
        if (retryAfter > 0) {
            throw new RateLimitExceededException(retryAfter);
        }
    }

    private String emailKey(String email) {
        try {
            byte[] normalized = email.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
