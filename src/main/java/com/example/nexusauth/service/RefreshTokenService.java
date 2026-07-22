package com.example.nexusauth.service;

import com.example.nexusauth.config.AuthProperties;
import com.example.nexusauth.model.ProfileType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(StringRedisTemplate redis, ObjectMapper mapper, AuthProperties properties) {
        this.redis = redis;
        this.mapper = mapper;
        this.properties = properties;
    }

    public IssuedRefreshToken issue(long profileId, ProfileType type, String familyId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = hash(raw);
        String family = familyId == null ? UUID.randomUUID().toString() : familyId;
        Instant expiresAt = Instant.now().plus(properties.refreshTokenTtl());
        RefreshSession session = new RefreshSession(profileId, type, family, expiresAt);
        redis.opsForValue().set(tokenKey(hash), json(session), properties.refreshTokenTtl());
        redis.opsForSet().add(profileKey(profileId), hash);
        redis.opsForSet().add(familyKey(family), hash);
        redis.expire(profileKey(profileId), properties.refreshTokenTtl());
        redis.expire(familyKey(family), properties.refreshTokenTtl());
        return new IssuedRefreshToken(raw, expiresAt, family);
    }

    public Rotation consume(String rawToken) {
        String hash = hash(rawToken);
        String payload = redis.opsForValue().getAndDelete(tokenKey(hash));
        if (payload == null) {
            String reusedFamily = redis.opsForValue().get(usedKey(hash));
            if (reusedFamily != null) revokeFamily(reusedFamily);
            throw new InvalidRefreshTokenException();
        }
        RefreshSession session = fromJson(payload);
        redis.opsForSet().remove(profileKey(session.profileId()), hash);
        redis.opsForSet().remove(familyKey(session.familyId()), hash);
        redis.opsForValue().set(usedKey(hash), session.familyId(), properties.refreshTokenTtl());
        return new Rotation(session, issue(session.profileId(), session.type(), session.familyId()));
    }

    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        String hash = hash(rawToken);
        String payload = redis.opsForValue().getAndDelete(tokenKey(hash));
        if (payload != null) {
            RefreshSession session = fromJson(payload);
            redis.opsForSet().remove(profileKey(session.profileId()), hash);
            redis.opsForSet().remove(familyKey(session.familyId()), hash);
        }
    }

    public void revokeAll(long profileId) {
        Set<String> hashes = redis.opsForSet().members(profileKey(profileId));
        if (hashes != null) hashes.forEach(hash -> redis.delete(tokenKey(hash)));
        redis.delete(profileKey(profileId));
    }

    private void revokeFamily(String familyId) {
        Set<String> hashes = redis.opsForSet().members(familyKey(familyId));
        if (hashes != null) hashes.forEach(hash -> redis.delete(tokenKey(hash)));
        redis.delete(familyKey(familyId));
    }

    private String json(RefreshSession value) {
        try { return mapper.writeValueAsString(value); }
        catch (JacksonException e) { throw new IllegalStateException(e); }
    }

    private RefreshSession fromJson(String value) {
        try { return mapper.readValue(value, RefreshSession.class); }
        catch (JacksonException e) { throw new IllegalStateException(e); }
    }

    private String hash(String token) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private String tokenKey(String hash) { return "auth:refresh:" + hash; }
    private String usedKey(String hash) { return "auth:refresh-used:" + hash; }
    private String profileKey(long id) { return "auth:profile-refresh:" + id; }
    private String familyKey(String id) { return "auth:refresh-family:" + id; }

    public record RefreshSession(long profileId, ProfileType type, String familyId, Instant expiresAt) {}
    public record IssuedRefreshToken(String value, Instant expiresAt, String familyId) {}
    public record Rotation(RefreshSession previous, IssuedRefreshToken current) {}
    public static class InvalidRefreshTokenException extends RuntimeException {}
}
