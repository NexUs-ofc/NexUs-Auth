package com.example.nexusauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String issuer,
        String jwtSecretBase64,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration registrationTtl,
        Duration otpTtl
) {}
