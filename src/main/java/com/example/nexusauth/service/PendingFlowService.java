package com.example.nexusauth.service;

import com.example.nexusauth.config.AuthProperties;
import com.example.nexusauth.model.RegistrationData;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PendingFlowService {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final PasswordEncoder encoder;
    private final AuthProperties properties;
    private final OtpMailService mail;
    private final SecureRandom random = new SecureRandom();

    public PendingFlowService(StringRedisTemplate redis, ObjectMapper mapper, PasswordEncoder encoder,
                              AuthProperties properties, OtpMailService mail) {
        this.redis = redis;
        this.mapper = mapper;
        this.encoder = encoder;
        this.properties = properties;
        this.mail = mail;
    }

    public String startRegistration(RegistrationData data) {
        String id = UUID.randomUUID().toString();
        String otp = "%06d".formatted(random.nextInt(1_000_000));
        PendingRegistration pending = new PendingRegistration(data, encoder.encode(otp));
        redis.opsForValue().set(registrationKey(id), json(pending), properties.registrationTtl());
        mail.send(data.email(), otp, "confirmar seu cadastro");
        return id;
    }

    public RegistrationData verifyRegistration(String id, String otp) {
        String payload = redis.opsForValue().get(registrationKey(id));
        if (payload == null) throw new InvalidOrExpiredOtpException();
        String attemptsKey = "auth:registration-attempts:" + id;
        int attempts = incrementAttempts(attemptsKey, properties.registrationTtl());
        if (attempts > 5) {
            redis.delete(registrationKey(id));
            redis.delete(attemptsKey);
            throw new InvalidOrExpiredOtpException();
        }
        PendingRegistration pending = fromJson(payload, PendingRegistration.class);
        if (!encoder.matches(otp, pending.otpHash())) throw new InvalidOrExpiredOtpException();
        return pending.data();
    }

    public void completeRegistration(String id) {
        redis.delete(registrationKey(id));
        redis.delete("auth:registration-attempts:" + id);
    }

    public String saveFirebaseTicket(FirebaseIdentityService.Identity identity) {
        String id = UUID.randomUUID().toString();
        redis.opsForValue().set(firebaseKey(id), json(identity), properties.registrationTtl());
        return id;
    }

    public FirebaseIdentityService.Identity getFirebaseTicket(String id) {
        String payload = redis.opsForValue().get(firebaseKey(id));
        if (payload == null) throw new ExpiredRegistrationException();
        return fromJson(payload, FirebaseIdentityService.Identity.class);
    }

    public void deleteFirebaseTicket(String id) {
        redis.delete(firebaseKey(id));
    }

    public String startPasswordReset(long profileId, String email) {
        String id = UUID.randomUUID().toString();
        String otp = "%06d".formatted(random.nextInt(1_000_000));
        redis.opsForValue().set(resetKey(id), json(new PendingReset(profileId, encoder.encode(otp))), properties.otpTtl());
        mail.send(email, otp, "recuperar sua senha");
        return id;
    }

    public long verifyPasswordReset(String id, String otp) {
        String payload = redis.opsForValue().get(resetKey(id));
        if (payload == null) throw new InvalidOrExpiredOtpException();
        String attemptsKey = "auth:reset-attempts:" + id;
        int attempts = incrementAttempts(attemptsKey, properties.otpTtl());
        if (attempts > 5) {
            redis.delete(resetKey(id));
            redis.delete(attemptsKey);
            throw new InvalidOrExpiredOtpException();
        }
        PendingReset pending = fromJson(payload, PendingReset.class);
        if (!encoder.matches(otp, pending.otpHash())) throw new InvalidOrExpiredOtpException();
        redis.delete(resetKey(id));
        redis.delete(attemptsKey);
        return pending.profileId();
    }

    private int incrementAttempts(String key, Duration ttl) {
        Long attempts = redis.opsForValue().increment(key);
        redis.expire(key, ttl);
        return attempts == null ? 1 : attempts.intValue();
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JacksonException e) { throw new IllegalStateException(e); }
    }

    private <T> T fromJson(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (JacksonException e) { throw new IllegalStateException(e); }
    }

    private String registrationKey(String id) { return "auth:registration:" + id; }
    private String firebaseKey(String id) { return "auth:firebase-ticket:" + id; }
    private String resetKey(String id) { return "auth:password-reset:" + id; }

    private record PendingRegistration(RegistrationData data, String otpHash) {}
    private record PendingReset(long profileId, String otpHash) {}
    public static class InvalidOrExpiredOtpException extends RuntimeException {}
    public static class ExpiredRegistrationException extends RuntimeException {}
}
