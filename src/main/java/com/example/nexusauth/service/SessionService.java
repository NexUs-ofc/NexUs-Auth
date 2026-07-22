package com.example.nexusauth.service;

import com.example.nexusauth.model.Profile;
import com.example.nexusauth.repository.ProfileRepository;
import com.example.nexusauth.security.JwtTokenService;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
    private final JwtTokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final ProfileRepository profiles;

    public SessionService(JwtTokenService accessTokens, RefreshTokenService refreshTokens, ProfileRepository profiles) {
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.profiles = profiles;
    }

    public Session issue(Profile profile) {
        JwtTokenService.AccessToken access = accessTokens.issue(profile);
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokens.issue(profile.id(), profile.type(), null);
        return new Session(access.value(), access.expiresAt(), refresh.value(), refresh.expiresAt());
    }

    public Session refresh(String rawRefreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokens.consume(rawRefreshToken);
        Profile profile = profiles.findById(Math.toIntExact(rotation.previous().profileId()))
                .orElseThrow(InvalidSessionException::new);
        if (profile.status() != com.example.nexusauth.model.ProfileStatus.ACTIVE) throw new InvalidSessionException();
        JwtTokenService.AccessToken access = accessTokens.issue(profile);
        return new Session(access.value(), access.expiresAt(), rotation.current().value(), rotation.current().expiresAt());
    }

    public record Session(String accessToken, Instant accessTokenExpiresAt,
                          String refreshToken, Instant refreshTokenExpiresAt) {}
    public static class InvalidSessionException extends RuntimeException {}
}
