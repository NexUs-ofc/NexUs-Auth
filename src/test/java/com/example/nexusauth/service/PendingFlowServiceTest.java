package com.example.nexusauth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.nexusauth.config.AuthProperties;
import com.example.nexusauth.dto.registration.RegistrationData;
import com.example.nexusauth.model.AuthProvider;
import com.example.nexusauth.model.ProfileType;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class PendingFlowServiceTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private PendingFlowService service;
    private OtpMailService mail;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        AuthProperties properties = new AuthProperties("issuer", "secret",
                Duration.ofMinutes(15), Duration.ofDays(30),
                Duration.ofMinutes(15), Duration.ofMinutes(10));
        mail = mock(OtpMailService.class);
        service = new PendingFlowService(redis, mock(ObjectMapper.class), mock(PasswordEncoder.class),
                properties, mail);
    }

    @Test
    void registrationEmailUsesTheSameValidityAsRedis() {
        RegistrationData data = new RegistrationData(ProfileType.HOUSEHOLD, "user@example.com", "User",
                null, null, null, null, null, AuthProvider.PASSWORD, "credential");

        String id = service.startRegistration(data);

        ArgumentCaptor<String> otp = ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq(data.email()), otp.capture(), eq(OtpMailService.Purpose.REGISTRATION),
                eq(Duration.ofMinutes(15)));
        assertThat(otp.getValue()).matches("\\d{6}");
        verify(values).set(eq("auth:registration:" + id), org.mockito.ArgumentMatchers.isNull(),
                eq(Duration.ofMinutes(15)));
    }

    @Test
    void passwordResetEmailUsesTheSameValidityAsRedis() {
        String id = service.startPasswordReset(42L, "user@example.com");

        ArgumentCaptor<String> otp = ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq("user@example.com"), otp.capture(), eq(OtpMailService.Purpose.PASSWORD_RESET),
                eq(Duration.ofMinutes(10)));
        assertThat(otp.getValue()).matches("\\d{6}");
        verify(values).set(eq("auth:password-reset:" + id), org.mockito.ArgumentMatchers.isNull(),
                eq(Duration.ofMinutes(10)));
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

    @Test
    @SuppressWarnings("unchecked")
    void issuesResetTicketOnlyAfterValidOtp() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches("000000", "otp-hash")).thenReturn(true);

        AuthProperties properties = new AuthProperties("issuer", "secret",
                Duration.ofMinutes(15), Duration.ofDays(30),
                Duration.ofMinutes(15), Duration.ofMinutes(10));
        PendingFlowService realJsonService = new PendingFlowService(redis, JsonMapper.builder().build(),
                encoder, properties, mock(OtpMailService.class));

        when(values.get("auth:password-reset:reset-id"))
                .thenReturn("{\"profileId\":42,\"otpHash\":\"otp-hash\"}");
        when(values.increment("auth:reset-attempts:reset-id")).thenReturn(1L);

        String ticket = realJsonService.verifyPasswordReset("reset-id", "000000");

        assertThat(ticket).isNotBlank();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(values).set(keyCaptor.capture(), eq("42"), eq(Duration.ofMinutes(5)));

        assertThat(keyCaptor.getValue()).isEqualTo("auth:password-reset-ticket:" + ticket);

        when(values.get("auth:password-reset-ticket:" + ticket)).thenReturn("42");

        long profileId = realJsonService.consumePasswordResetTicket(ticket);

        assertThat(profileId).isEqualTo(42L);

        verify(redis).delete("auth:password-reset-ticket:" + ticket);
    }

    @Test
    void rejectsUnknownOrExpiredResetTicket() {
        when(values.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.consumePasswordResetTicket("unknown-ticket"))
                .isInstanceOf(PendingFlowService.InvalidOrExpiredResetTicketException.class);
    }
}
