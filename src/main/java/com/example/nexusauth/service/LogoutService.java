package com.example.nexusauth.service;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class LogoutService {

    private static final Logger logger =
            LoggerFactory.getLogger(LogoutService.class);

    private final StringRedisTemplate redis;
    private final RefreshTokenService refreshTokens;

    public LogoutService(
            StringRedisTemplate redis,
            RefreshTokenService refreshTokens
    ) {
        this.redis = redis;
        this.refreshTokens = refreshTokens;
    }

    public void logout(
            Jwt jwt,
            String refreshToken
    ) {

        logger.info(
                "Iniciando processo de logout"
        );

        Duration remaining =
                Duration.between(
                        Instant.now(),
                        jwt.getExpiresAt()
                );

        logger.debug(
                "Tempo restante de validade do JWT calculado: {} segundos",
                remaining.getSeconds()
        );

        if (!remaining.isNegative() &&
                !remaining.isZero() &&
                jwt.getId() != null) {

            String blacklistKey =
                    "auth:blacklist:" + jwt.getId();

            redis.opsForValue().set(
                    blacklistKey,
                    "1",
                    remaining
            );

            logger.info(
                    "JWT adicionado à blacklist com sucesso jwtId={} expiracaoEmSegundos={}",
                    jwt.getId(),
                    remaining.getSeconds()
            );

        } else {

            logger.debug(
                    "JWT não foi adicionado à blacklist devido à expiração ou ausência de identificador jwtId={} restanteSegundos={}",
                    jwt.getId(),
                    remaining.getSeconds()
            );
        }

        logger.debug(
                "Iniciando revogação do refresh token"
        );

        refreshTokens.revoke(
                refreshToken
        );

        logger.info(
                "Logout concluído com sucesso jwtId={}",
                jwt.getId()
        );
    }
}