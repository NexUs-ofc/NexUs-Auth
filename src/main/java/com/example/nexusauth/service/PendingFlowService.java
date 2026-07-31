package com.example.nexusauth.service;

import com.example.nexusauth.config.AuthProperties;
import com.example.nexusauth.model.RegistrationData;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class PendingFlowService {

    private static final Logger logger =
            LoggerFactory.getLogger(PendingFlowService.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final PasswordEncoder encoder;
    private final AuthProperties properties;
    private final OtpMailService mail;
    private final SecureRandom random = new SecureRandom();

    public PendingFlowService(
            StringRedisTemplate redis,
            ObjectMapper mapper,
            PasswordEncoder encoder,
            AuthProperties properties,
            OtpMailService mail
    ) {
        this.redis = redis;
        this.mapper = mapper;
        this.encoder = encoder;
        this.properties = properties;
        this.mail = mail;
    }

    public String startRegistration(
            RegistrationData data
    ) {

        logger.info(
                "Iniciando fluxo de cadastro pendente email={} type={} provider={}",
                data.email(),
                data.type(),
                data.provider()
        );

        String id =
                UUID.randomUUID().toString();

        String otp =
                "%06d".formatted(
                        random.nextInt(1_000_000)
                );

        PendingRegistration pending =
                new PendingRegistration(
                        data,
                        encoder.encode(otp)
                );

        redis.opsForValue().set(
                registrationKey(id),
                json(pending),
                properties.registrationTtl()
        );

        logger.debug(
                "Cadastro pendente armazenado no Redis registrationId={} ttl={} segundos",
                id,
                properties.registrationTtl().getSeconds()
        );

        mail.send(
                data.email(),
                otp,
                "confirmar seu cadastro"
        );

        logger.info(
                "Fluxo de cadastro iniciado com sucesso registrationId={} email={}",
                id,
                data.email()
        );

        return id;
    }

    public RegistrationData verifyRegistration(
            String id,
            String otp
    ) {

        logger.info(
                "Iniciando verificação de cadastro registrationId={}",
                id
        );

        String payload =
                redis.opsForValue().get(
                        registrationKey(id)
                );

        if (payload == null) {

            logger.warn(
                    "Cadastro pendente não encontrado ou expirado registrationId={}",
                    id
            );

            throw new InvalidOrExpiredOtpException();
        }

        String attemptsKey =
                "auth:registration-attempts:" + id;

        int attempts =
                incrementAttempts(
                        attemptsKey,
                        properties.registrationTtl()
                );

        logger.debug(
                "Tentativa de verificação de cadastro registrada registrationId={} tentativa={}",
                id,
                attempts
        );

        if (attempts > 5) {

            logger.warn(
                    "Limite de tentativas de verificação de cadastro excedido registrationId={} tentativas={}",
                    id,
                    attempts
            );

            redis.delete(
                    registrationKey(id)
            );

            redis.delete(
                    attemptsKey
            );

            throw new InvalidOrExpiredOtpException();
        }

        PendingRegistration pending =
                fromJson(
                        payload,
                        PendingRegistration.class
                );

        if (!encoder.matches(
                otp,
                pending.otpHash()
        )) {

            logger.warn(
                    "OTP de cadastro inválido registrationId={} tentativa={}",
                    id,
                    attempts
            );

            throw new InvalidOrExpiredOtpException();
        }

        logger.info(
                "OTP de cadastro validado com sucesso registrationId={} email={}",
                id,
                pending.data().email()
        );

        return pending.data();
    }

    public void completeRegistration(
            String id
    ) {

        logger.info(
                "Finalizando fluxo de cadastro pendente registrationId={}",
                id
        );

        redis.delete(
                registrationKey(id)
        );

        redis.delete(
                "auth:registration-attempts:" + id
        );

        logger.debug(
                "Dados de cadastro pendente e tentativas removidos do Redis registrationId={}",
                id
        );
    }

    public String saveFirebaseTicket(
            FirebaseIdentityService.Identity identity
    ) {

        logger.info(
                "Armazenando ticket de cadastro Firebase email={} provider={}",
                identity.email(),
                identity.provider()
        );

        String id =
                UUID.randomUUID().toString();

        redis.opsForValue().set(
                firebaseKey(id),
                json(identity),
                properties.registrationTtl()
        );

        logger.debug(
                "Ticket Firebase armazenado com sucesso firebaseTicket={} ttl={} segundos",
                id,
                properties.registrationTtl().getSeconds()
        );

        return id;
    }

    public FirebaseIdentityService.Identity getFirebaseTicket(
            String id
    ) {

        logger.info(
                "Consultando ticket de cadastro Firebase firebaseTicket={}",
                id
        );

        String payload =
                redis.opsForValue().get(
                        firebaseKey(id)
                );

        if (payload == null) {

            logger.warn(
                    "Ticket de cadastro Firebase não encontrado ou expirado firebaseTicket={}",
                    id
            );

            throw new ExpiredRegistrationException();
        }

        FirebaseIdentityService.Identity identity =
                fromJson(
                        payload,
                        FirebaseIdentityService.Identity.class
                );

        logger.debug(
                "Ticket Firebase recuperado com sucesso firebaseTicket={} email={} provider={}",
                id,
                identity.email(),
                identity.provider()
        );

        return identity;
    }

    public void deleteFirebaseTicket(
            String id
    ) {

        logger.info(
                "Removendo ticket de cadastro Firebase firebaseTicket={}",
                id
        );

        redis.delete(
                firebaseKey(id)
        );

        logger.debug(
                "Ticket Firebase removido do Redis firebaseTicket={}",
                id
        );
    }

    public String startPasswordReset(
            long profileId,
            String email
    ) {

        logger.info(
                "Iniciando fluxo de recuperação de senha profileId={} email={}",
                profileId,
                email
        );

        String id =
                UUID.randomUUID().toString();

        String otp =
                "%06d".formatted(
                        random.nextInt(1_000_000)
                );

        redis.opsForValue().set(
                resetKey(id),
                json(
                        new PendingReset(
                                profileId,
                                encoder.encode(otp)
                        )
                ),
                properties.otpTtl()
        );

        logger.debug(
                "Dados de recuperação de senha armazenados no Redis resetId={} profileId={} ttl={} segundos",
                id,
                profileId,
                properties.otpTtl().getSeconds()
        );

        mail.send(
                email,
                otp,
                "recuperar sua senha"
        );

        logger.info(
                "Fluxo de recuperação de senha iniciado com sucesso resetId={} profileId={}",
                id,
                profileId
        );

        return id;
    }

    public long verifyPasswordReset(
            String id,
            String otp
    ) {

        logger.info(
                "Iniciando verificação de recuperação de senha resetId={}",
                id
        );

        String payload =
                redis.opsForValue().get(
                        resetKey(id)
                );

        if (payload == null) {

            logger.warn(
                    "Processo de recuperação de senha não encontrado ou expirado resetId={}",
                    id
            );

            throw new InvalidOrExpiredOtpException();
        }

        String attemptsKey =
                "auth:reset-attempts:" + id;

        int attempts =
                incrementAttempts(
                        attemptsKey,
                        properties.otpTtl()
                );

        logger.debug(
                "Tentativa de recuperação de senha registrada resetId={} tentativa={}",
                id,
                attempts
        );

        if (attempts > 5) {

            logger.warn(
                    "Limite de tentativas de recuperação de senha excedido resetId={} tentativas={}",
                    id,
                    attempts
            );

            redis.delete(
                    resetKey(id)
            );

            redis.delete(
                    attemptsKey
            );

            throw new InvalidOrExpiredOtpException();
        }

        PendingReset pending =
                fromJson(
                        payload,
                        PendingReset.class
                );

        if (!encoder.matches(
                otp,
                pending.otpHash()
        )) {

            logger.warn(
                    "OTP de recuperação de senha inválido resetId={} tentativa={}",
                    id,
                    attempts
            );

            throw new InvalidOrExpiredOtpException();
        }

        redis.delete(
                resetKey(id)
        );

        redis.delete(
                attemptsKey
        );

        logger.info(
                "Recuperação de senha validada com sucesso resetId={} profileId={}",
                id,
                pending.profileId()
        );

        return pending.profileId();
    }

    private int incrementAttempts(
            String key,
            Duration ttl
    ) {

        Long attempts =
                redis.opsForValue().increment(key);

        redis.expire(
                key,
                ttl
        );

        int currentAttempts =
                attempts == null
                        ? 1
                        : attempts.intValue();

        logger.debug(
                "Contador de tentativas atualizado key={} tentativa={} ttl={} segundos",
                key,
                currentAttempts,
                ttl.getSeconds()
        );

        return currentAttempts;
    }

    private String json(
            Object value
    ) {

        try {

            return mapper.writeValueAsString(
                    value
            );

        } catch (JacksonException e) {

            logger.error(
                    "Erro ao serializar objeto para JSON type={}",
                    value.getClass().getSimpleName(),
                    e
            );

            throw new IllegalStateException(e);
        }
    }

    private <T> T fromJson(
            String value,
            Class<T> type
    ) {

        try {

            return mapper.readValue(
                    value,
                    type
            );

        } catch (JacksonException e) {

            logger.error(
                    "Erro ao desserializar JSON para objeto type={}",
                    type.getSimpleName(),
                    e
            );

            throw new IllegalStateException(e);
        }
    }

    private String registrationKey(
            String id
    ) {
        return "auth:registration:" + id;
    }

    private String firebaseKey(
            String id
    ) {
        return "auth:firebase-ticket:" + id;
    }

    private String resetKey(
            String id
    ) {
        return "auth:password-reset:" + id;
    }

    private record PendingRegistration(
            RegistrationData data,
            String otpHash
    ) {}

    private record PendingReset(
            long profileId,
            String otpHash
    ) {}

    public static class InvalidOrExpiredOtpException
            extends RuntimeException {}

    public static class ExpiredRegistrationException
            extends RuntimeException {}
}