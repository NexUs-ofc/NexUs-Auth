package com.example.nexusauth.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class JwtBlacklistValidator implements OAuth2TokenValidator<Jwt> {
    private static final OAuth2Error REVOKED = new OAuth2Error("invalid_token", "Token revogado", null);
    private final StringRedisTemplate redis;

    public JwtBlacklistValidator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String jti = jwt.getId();
        return jti != null && Boolean.TRUE.equals(redis.hasKey("auth:blacklist:" + jti))
                ? OAuth2TokenValidatorResult.failure(REVOKED)
                : OAuth2TokenValidatorResult.success();
    }
}
