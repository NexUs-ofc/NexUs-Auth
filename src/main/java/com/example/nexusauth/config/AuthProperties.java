package com.example.nexusauth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String issuer,
        String jwtSecretBase64,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration registrationTtl,
        Duration otpTtl
) {}
