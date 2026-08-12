package com.example.nexusauth.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class LogoutService {
    private final StringRedisTemplate redis;
    private final RefreshTokenService refreshTokens;

    public LogoutService(StringRedisTemplate redis, RefreshTokenService refreshTokens) {
        this.redis = redis;
        this.refreshTokens = refreshTokens;
    }

    public void logout(Jwt jwt, String refreshToken) {
        Duration remaining = Duration.between(Instant.now(), jwt.getExpiresAt());
        if (!remaining.isNegative() && !remaining.isZero() && jwt.getId() != null) {
            redis.opsForValue().set("auth:blacklist:" + jwt.getId(), "1", remaining);
        }
        refreshTokens.revoke(refreshToken);
    }
}
