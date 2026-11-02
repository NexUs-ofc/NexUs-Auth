package com.example.nexusauth.ratelimit;

import com.example.nexusauth.controller.AuthController;
import com.example.nexusauth.exception.ApiExceptionHandler;
import com.example.nexusauth.service.AuthService;
import com.example.nexusauth.service.LogoutService;
import com.example.nexusauth.service.RefreshTokenService;
import com.example.nexusauth.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthRateLimitControllerTest {

    private final AtomicLong now = new AtomicLong();
    private final AuthService auth = mock(AuthService.class);
    private final SessionService sessions = mock(SessionService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var properties = new RateLimitProperties(1000, 120, 12, Duration.ofMinutes(5), 1, 5, 30, "");
        var limits = new AuthRateLimitService(new InMemoryRateLimiter(1000, now::get),
                properties, new ClientIpResolver(""));
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth, sessions, mock(LogoutService.class), limits))
                .setControllerAdvice(new ApiExceptionHandler()).addFilters(new RateLimitFilter(limits)).build();
    }

    @Test
    void rejectsTheThirteenthLoginBeforeCheckingCredentials() throws Exception {
        when(auth.passwordLogin(any())).thenThrow(new AuthService.InvalidCredentialsException());
        String body = """
                {"email":"user@example.com","password":"wrong","channel":"MOBILE"}
                """;
        for (int index = 0; index < 12; index++) {
            mvc.perform(post("/api/auth/login/password").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login/password").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "300"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
        verify(auth, times(12)).passwordLogin(any());
    }

    @Test
    void sharesTheOtpSendQuotaAcrossRegistrationAndPasswordRecovery() throws Exception {
        when(auth.startPasswordRegistration(any())).thenReturn("registration-id");
        when(auth.startPasswordReset(any())).thenReturn("reset-id");
        String registration = """
                {"type":"HOUSEHOLD","email":"user@example.com","password":"password123","name":"User",
                 "address":{"neighborhood":"Centro","street":"Rua A","number":"10","cep":"01001000",
                 "city":"Sao Paulo","state":"SP"}}
                """;
        mvc.perform(post("/api/auth/registrations/password/start").contentType(MediaType.APPLICATION_JSON)
                        .content(registration)).andExpect(status().isAccepted());
        String recovery = "{\"email\":\"USER@example.com\"}";
        mvc.perform(post("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON).content(recovery))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
        for (int index = 1; index < 5; index++) {
            now.set(Duration.ofMinutes(index).toNanos());
            mvc.perform(post("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON).content(recovery))
                    .andExpect(status().isAccepted());
        }
        now.set(Duration.ofMinutes(5).toNanos());
        mvc.perform(post("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON).content(recovery))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "600"));
        verify(auth, times(4)).startPasswordReset(any());
    }

    @Test
    void rejectsExcessiveRefreshesBeforeRotatingTokens() throws Exception {
        when(sessions.refresh(any())).thenThrow(new RefreshTokenService.InvalidRefreshTokenException());
        String body = "{\"refreshToken\":\"invalid\"}";
        for (int index = 0; index < 30; index++) {
            mvc.perform(post("/api/auth/token/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/token/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
        verify(sessions, times(30)).refresh(any());
    }
}
