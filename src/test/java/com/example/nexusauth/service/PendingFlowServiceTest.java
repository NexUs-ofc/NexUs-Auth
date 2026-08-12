package com.example.nexusauth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.nexusauth.config.AuthProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

class PendingFlowServiceTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private PendingFlowService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        AuthProperties properties = new AuthProperties("issuer", "secret",
                Duration.ofMinutes(15), Duration.ofDays(30),
                Duration.ofMinutes(15), Duration.ofMinutes(10));
        service = new PendingFlowService(redis, mock(ObjectMapper.class), mock(PasswordEncoder.class),
                properties, mock(OtpMailService.class));
    }

    @Test
    void invalidatesRegistrationAfterAttemptLimit() {
        when(values.get("auth:registration:registration-id")).thenReturn("pending");
        when(values.increment("auth:registration-attempts:registration-id")).thenReturn(6L);

        assertThatThrownBy(() -> service.verifyRegistration("registration-id", "000000"))
                .isInstanceOf(PendingFlowService.InvalidOrExpiredOtpException.class);

        verify(redis).delete("auth:registration:registration-id");
        verify(redis).delete("auth:registration-attempts:registration-id");
        verify(redis).expire("auth:registration-attempts:registration-id", Duration.ofMinutes(15));
    }

    @Test
    void invalidatesPasswordResetAfterAttemptLimit() {
        when(values.get("auth:password-reset:reset-id")).thenReturn("pending");
        when(values.increment("auth:reset-attempts:reset-id")).thenReturn(6L);

        assertThatThrownBy(() -> service.verifyPasswordReset("reset-id", "000000"))
                .isInstanceOf(PendingFlowService.InvalidOrExpiredOtpException.class);

        verify(redis).delete("auth:password-reset:reset-id");
        verify(redis).delete("auth:reset-attempts:reset-id");
        verify(redis).expire("auth:reset-attempts:reset-id", Duration.ofMinutes(10));
    }
}
