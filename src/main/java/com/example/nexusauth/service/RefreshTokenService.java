package com.example.nexusauth.service;

import com.example.nexusauth.config.AuthProperties;
import com.example.nexusauth.model.ProfileType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class RefreshTokenService {

    private static final Logger logger =
            LoggerFactory.getLogger(RefreshTokenService.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(
            StringRedisTemplate redis,
            ObjectMapper mapper,
            AuthProperties properties
    ) {
        this.redis = redis;
        this.mapper = mapper;
        this.properties = properties;
    }

    public IssuedRefreshToken issue(
            long profileId,
            ProfileType type,
            String familyId
    ) {

        logger.info(
                "Iniciando emissão de refresh token profileId={} type={} possuiFamilyId={}",
                profileId,
                type,
                familyId != null
        );

        byte[] bytes =
                new byte[32];

        random.nextBytes(bytes);

        String raw =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(bytes);

        String hash =
                hash(raw);

        String family =
                familyId == null
                        ? UUID.randomUUID().toString()
                        : familyId;

        Instant expiresAt =
                Instant.now().plus(
                        properties.refreshTokenTtl()
                );

        RefreshSession session =
                new RefreshSession(
                        profileId,
                        type,
                        family,
                        expiresAt
                );

        redis.opsForValue().set(
                tokenKey(hash),
                json(session),
                properties.refreshTokenTtl()
        );

        redis.opsForSet().add(
                profileKey(profileId),
                hash
        );

        redis.opsForSet().add(
                familyKey(family),
                hash
        );

        redis.expire(
                profileKey(profileId),
                properties.refreshTokenTtl()
        );

        redis.expire(
                familyKey(family),
                properties.refreshTokenTtl()
        );

        logger.info(
                "Refresh token emitido com sucesso profileId={} type={} familyId={} expiresAt={}",
                profileId,
                type,
                family,
                expiresAt
        );

        return new IssuedRefreshToken(
                raw,
                expiresAt,
                family
        );
    }

    public Rotation consume(
            String rawToken
    ) {

        logger.info(
                "Iniciando consumo e rotação de refresh token"
        );

        String hash =
                hash(rawToken);

        String payload =
                redis.opsForValue().getAndDelete(
                        tokenKey(hash)
                );

        if (payload == null) {

            logger.warn(
                    "Refresh token não encontrado ou já utilizado hashPrefix={}",
                    hashPrefix(hash)
            );

            String reusedFamily =
                    redis.opsForValue().get(
                            usedKey(hash)
                    );

            if (reusedFamily != null) {

                logger.warn(
                        "Reutilização de refresh token detectada; revogando família inteira familyId={}",
                        reusedFamily
                );

                revokeFamily(
                        reusedFamily
                );
            }

            throw new InvalidRefreshTokenException();
        }

        RefreshSession session =
                fromJson(payload);

        logger.debug(
                "Refresh token válido encontrado profileId={} type={} familyId={}",
                session.profileId(),
                session.type(),
                session.familyId()
        );

        redis.opsForSet().remove(
                profileKey(
                        session.profileId()
                ),
                hash
        );

        redis.opsForSet().remove(
                familyKey(
                        session.familyId()
                ),
                hash
        );

        redis.opsForValue().set(
                usedKey(hash),
                session.familyId(),
                properties.refreshTokenTtl()
        );

        IssuedRefreshToken current =
                issue(
                        session.profileId(),
                        session.type(),
                        session.familyId()
                );

        logger.info(
                "Refresh token rotacionado com sucesso profileId={} familyId={}",
                session.profileId(),
                session.familyId()
        );

        return new Rotation(
                session,
                current
        );
    }

    public void revoke(
            String rawToken
    ) {

        logger.info(
                "Iniciando revogação de refresh token"
        );

        if (rawToken == null ||
                rawToken.isBlank()) {

            logger.debug(
                    "Revogação ignorada devido a refresh token nulo ou vazio"
            );

            return;
        }

        String hash =
                hash(rawToken);

        String payload =
                redis.opsForValue().getAndDelete(
                        tokenKey(hash)
                );

        if (payload != null) {

            RefreshSession session =
                    fromJson(payload);

            redis.opsForSet().remove(
                    profileKey(
                            session.profileId()
                    ),
                    hash
            );

            redis.opsForSet().remove(
                    familyKey(
                            session.familyId()
                    ),
                    hash
            );

            logger.info(
                    "Refresh token revogado com sucesso profileId={} familyId={}",
                    session.profileId(),
                    session.familyId()
            );

        } else {

            logger.debug(
                    "Nenhum refresh token ativo encontrado para revogação hashPrefix={}",
                    hashPrefix(hash)
            );
        }
    }

    public void revokeAll(
            long profileId
    ) {

        logger.info(
                "Iniciando revogação de todos os refresh tokens do perfil profileId={}",
                profileId
        );

        Set<String> hashes =
                redis.opsForSet().members(
                        profileKey(profileId)
                );

        if (hashes != null) {

            logger.debug(
                    "Refresh tokens ativos encontrados para revogação profileId={} quantidade={}",
                    profileId,
                    hashes.size()
            );

            hashes.forEach(
                    hash -> redis.delete(
                            tokenKey(hash)
                    )
            );

        } else {

            logger.debug(
                    "Nenhum refresh token ativo encontrado para o perfil profileId={}",
                    profileId
            );
        }

        redis.delete(
                profileKey(profileId)
        );

        logger.info(
                "Todos os refresh tokens do perfil foram revogados com sucesso profileId={}",
                profileId
        );
    }

    private void revokeFamily(
            String familyId
    ) {

        logger.warn(
                "Iniciando revogação de família de refresh tokens familyId={}",
                familyId
        );

        Set<String> hashes =
                redis.opsForSet().members(
                        familyKey(familyId)
                );

        if (hashes != null) {

            logger.debug(
                    "Refresh tokens encontrados na família para revogação familyId={} quantidade={}",
                    familyId,
                    hashes.size()
            );

            hashes.forEach(
                    hash -> redis.delete(
                            tokenKey(hash)
                    )
            );

        } else {

            logger.debug(
                    "Nenhum refresh token ativo encontrado na família familyId={}",
                    familyId
            );
        }

        redis.delete(
                familyKey(familyId)
        );

        logger.warn(
                "Família de refresh tokens revogada familyId={}",
                familyId
        );
    }

    private String json(
            RefreshSession value
    ) {

        try {

            return mapper.writeValueAsString(
                    value
            );

        } catch (JacksonException e) {

            logger.error(
                    "Erro ao serializar sessão de refresh token para JSON profileId={} familyId={}",
                    value.profileId(),
                    value.familyId(),
                    e
            );

            throw new IllegalStateException(e);
        }
    }

    private RefreshSession fromJson(
            String value
    ) {

        try {

            return mapper.readValue(
                    value,
                    RefreshSession.class
            );

        } catch (JacksonException e) {

            logger.error(
                    "Erro ao desserializar sessão de refresh token"
            );

            throw new IllegalStateException(e);
        }
    }

    private String hash(
            String token
    ) {

        try {

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                            MessageDigest
                                    .getInstance("SHA-256")
                                    .digest(
                                            token.getBytes(
                                                    StandardCharsets.UTF_8
                                            )
                                    )
                    );

        } catch (NoSuchAlgorithmException e) {

            logger.error(
                    "Algoritmo SHA-256 não disponível para geração do hash do refresh token",
                    e
            );

            throw new IllegalStateException(e);
        }
    }

    private String hashPrefix(
            String hash
    ) {

        return hash.length() <= 8
                ? hash
                : hash.substring(0, 8);
    }

    private String tokenKey(
            String hash
    ) {
        return "auth:refresh:" + hash;
    }

    private String usedKey(
            String hash
    ) {
        return "auth:refresh-used:" + hash;
    }

    private String profileKey(
            long id
    ) {
        return "auth:profile-refresh:" + id;
    }

    private String familyKey(
            String id
    ) {
        return "auth:refresh-family:" + id;
    }

    public record RefreshSession(
            long profileId,
            ProfileType type,
            String familyId,
            Instant expiresAt
    ) {}

    public record IssuedRefreshToken(
            String value,
            Instant expiresAt,
            String familyId
    ) {}

    public record Rotation(
            RefreshSession previous,
            IssuedRefreshToken current
    ) {}

    public static class InvalidRefreshTokenException
            extends RuntimeException {}
}