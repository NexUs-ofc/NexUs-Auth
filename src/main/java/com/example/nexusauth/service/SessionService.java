package com.example.nexusauth.service;

import com.example.nexusauth.model.Profile;
import com.example.nexusauth.model.ProfileStatus;
import com.example.nexusauth.repository.ProfileRepository;
import com.example.nexusauth.security.JwtTokenService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    SessionService.class
            );

    private final JwtTokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final ProfileRepository profiles;

    public SessionService(
            JwtTokenService accessTokens,
            RefreshTokenService refreshTokens,
            ProfileRepository profiles
    ) {
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.profiles = profiles;
    }

    public Session issue(
            Profile profile
    ) {

        logger.info(
                "Iniciando emissão de nova sessão profileId={} type={}",
                profile.id(),
                profile.type()
        );

        JwtTokenService.AccessToken access =
                accessTokens.issue(
                        profile
                );

        logger.debug(
                "Access token emitido com sucesso profileId={} expiresAt={}",
                profile.id(),
                access.expiresAt()
        );

        RefreshTokenService.IssuedRefreshToken refresh =
                refreshTokens.issue(
                        profile.id(),
                        profile.type(),
                        null
                );

        logger.debug(
                "Refresh token emitido com sucesso profileId={} familyId={} expiresAt={}",
                profile.id(),
                refresh.familyId(),
                refresh.expiresAt()
        );

        Session session =
                new Session(
                        access.value(),
                        access.expiresAt(),
                        refresh.value(),
                        refresh.expiresAt()
                );

        logger.info(
                "Sessão criada com sucesso profileId={} accessTokenExpiresAt={} refreshTokenExpiresAt={}",
                profile.id(),
                access.expiresAt(),
                refresh.expiresAt()
        );

        return session;
    }

    public Session refresh(
            String rawRefreshToken
    ) {

        logger.info(
                "Iniciando renovação de sessão através de refresh token"
        );

        RefreshTokenService.Rotation rotation =
                refreshTokens.consume(
                        rawRefreshToken
                );

        logger.debug(
                "Refresh token consumido e rotacionado com sucesso profileId={} familyId={}",
                rotation.previous().profileId(),
                rotation.previous().familyId()
        );

        Profile profile =
                profiles.findById(
                        Math.toIntExact(
                                rotation.previous().profileId()
                        )
                ).orElseThrow(
                        () -> {

                            logger.warn(
                                    "Perfil associado ao refresh token não encontrado profileId={}",
                                    rotation.previous().profileId()
                            );

                            return new InvalidSessionException();
                        }
                );

        logger.debug(
                "Perfil recuperado para renovação de sessão profileId={} status={} type={}",
                profile.id(),
                profile.status(),
                profile.type()
        );

        if (profile.status() != ProfileStatus.ACTIVE) {

            logger.warn(
                    "Tentativa de renovar sessão para perfil que não está ativo profileId={} status={}",
                    profile.id(),
                    profile.status()
            );

            throw new InvalidSessionException();
        }

        JwtTokenService.AccessToken access =
                accessTokens.issue(
                        profile
                );

        logger.debug(
                "Novo access token emitido durante renovação profileId={} expiresAt={}",
                profile.id(),
                access.expiresAt()
        );

        Session session =
                new Session(
                        access.value(),
                        access.expiresAt(),
                        rotation.current().value(),
                        rotation.current().expiresAt()
                );

        logger.info(
                "Sessão renovada com sucesso profileId={} familyId={} accessTokenExpiresAt={} refreshTokenExpiresAt={}",
                profile.id(),
                rotation.current().familyId(),
                access.expiresAt(),
                rotation.current().expiresAt()
        );

        return session;
    }

    public record Session(
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt
    ) {}

    public static class InvalidSessionException
            extends RuntimeException {}
}