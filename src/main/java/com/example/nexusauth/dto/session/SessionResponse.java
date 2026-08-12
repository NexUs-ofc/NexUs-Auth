package com.example.nexusauth.dto.session;

import java.time.Instant;

public record SessionResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {}
