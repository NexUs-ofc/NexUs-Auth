package com.example.nexusauth.security;

import com.example.nexusauth.ratelimit.AuthRateLimitService;
import com.example.nexusauth.service.AuthService;
import com.example.nexusauth.service.LogoutService;
import com.example.nexusauth.service.SessionService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "app.api-key=security-test-key",
    "app.admin.key=security-admin-key",
    "CORS_ALLOWED_ORIGINS=https://frontend.example.com",
    "spring.config.import="
})
@AutoConfigureMockMvc
class AuthSecurityIntegrationTest {

    private static final String API_KEY = "security-test-key";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtEncoder encoder;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private SessionService sessions;

    @MockitoBean
    private LogoutService logout;

    @MockitoBean
    private AuthRateLimitService rateLimits;

    @MockitoBean
    private StringRedisTemplate redis;

    @ParameterizedTest
    @MethodSource("protectedRoutes")
    void requiresJwtOnProtectedRoutes(String route, String body) throws Exception {
        mvc.perform(post(route).header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(auth, sessions, logout);
    }

    @ParameterizedTest
    @MethodSource("protectedRoutes")
    void rejectsNonJwtAuthentication(String route, String body) throws Exception {
        mvc.perform(post(route).with(user("other-authentication")).header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auth, sessions, logout);
    }

    @ParameterizedTest
    @MethodSource("protectedRoutes")
    void rejectsInvalidTokensBeforeCallingServices(String route, String body) throws Exception {
        Instant now = Instant.now();
        JwtEncoder otherEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(new byte[32], "HmacSHA256")));
        String[] tokens = {
            "malformed-token",
            token(otherEncoder, "nexus-auth-test", now.minusSeconds(30), now.plusSeconds(300), "wrong-signature"),
            token(encoder, "wrong-issuer", now.minusSeconds(30), now.plusSeconds(300), "wrong-issuer"),
            token(encoder, "nexus-auth-test", now.minusSeconds(300), now.minusSeconds(120), "expired"),
            token(encoder, "nexus-auth-test", now.minusSeconds(30), now.plusSeconds(300), "revoked")
        };
        when(redis.hasKey("auth:blacklist:revoked")).thenReturn(true);
        for (String value : tokens) {
            mvc.perform(post(route).header("X-API-KEY", API_KEY)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + value)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(auth, sessions, logout);
    }

    @ParameterizedTest
    @MethodSource("protectedRoutes")
    void requiresApiKeyEvenWithValidJwt(String route, String body) throws Exception {
        String value = validToken();
        mvc.perform(post(route).header(HttpHeaders.AUTHORIZATION, "Bearer " + value)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(route).header("X-API-KEY", "wrong-key")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + value)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(auth, sessions, logout);
    }

    @ParameterizedTest
    @MethodSource("protectedRoutes")
    void usesVerifiedJwtIdentityOnProtectedRoutes(String route, String body) throws Exception {
        mvc.perform(post(route).header("X-API-KEY", API_KEY)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        switch (route) {
            case "/api/auth/logout" -> verify(logout).logout(
                    argThat((Jwt jwt) -> "42".equals(jwt.getSubject()) && "active".equals(jwt.getId())),
                    eq("refresh-token"));
            case "/api/auth/google/link" -> verify(auth).linkGoogle(42L, "google-id-token");
            case "/api/auth/password/link" -> verify(auth).linkPassword(42L, "new-password");
            default -> throw new IllegalArgumentException(route);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/auth/registrations/password/start", "/api/auth/registrations/verify",
        "/api/auth/registrations/google", "/api/auth/login/password", "/api/auth/google/authenticate",
        "/api/auth/token/refresh", "/api/auth/password/forgot", "/api/auth/password/reset/verify",
        "/api/auth/password/reset"
    })
    void publicRoutesRequireApiKeyButNotJwt(String route) throws Exception {
        mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(route).header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(auth, sessions, logout);
    }

    @Test
    void passwordLoginStillReturnsSessionWithoutBearer() throws Exception {
        Instant expiresAt = Instant.now().plusSeconds(300);
        when(auth.passwordLogin(any())).thenReturn(new SessionService.Session(
                "access-token", expiresAt, "refresh-token", expiresAt));
        mvc.perform(post("/api/auth/login/password").header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email":"user@example.com","password":"password123","channel":"MOBILE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/auth/google/new-operation", "/api/auth/password/new-operation",
        "/api/auth/registrations/new-operation"
    })
    void doesNotMakeWholeAuthenticationNamespacesPublic(String route) throws Exception {
        mvc.perform(post(route).header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(auth, sessions, logout);
    }

    @Test
    void keepsCorsPreflightPublicAndRejectsUnlistedOrigins() throws Exception {
        mvc.perform(options("/api/auth/google/link")
                        .header(HttpHeaders.ORIGIN, "https://frontend.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-API-KEY,Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://frontend.example.com"));
        mvc.perform(options("/api/auth/google/link").header(HttpHeaders.ORIGIN, "https://untrusted.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auth, sessions, logout, rateLimits);
    }

    @Test
    void keepsSwaggerPublicAndAdministrationSeparate() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/admin/session").header("X-API-KEY", API_KEY)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/session").header("X-Admin-Key", "security-admin-key"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.autenticado").value(true));
    }

    private String validToken() {
        Instant now = Instant.now();
        return token(encoder, "nexus-auth-test", now.minusSeconds(30), now.plusSeconds(300), "active");
    }

    private String token(JwtEncoder jwtEncoder, String issuer, Instant issuedAt, Instant expiresAt, String id) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject("42").issuedAt(issuedAt)
                .expiresAt(expiresAt).id(id).claim("profile_type", "HOUSEHOLD")
                .claim("email", "user@example.com").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private static Stream<Arguments> protectedRoutes() {
        return Stream.of(
                Arguments.of("/api/auth/logout", "{\"refreshToken\":\"refresh-token\"}"),
                Arguments.of("/api/auth/google/link", "{\"idToken\":\"google-id-token\"}"),
                Arguments.of("/api/auth/password/link", "{\"newPassword\":\"new-password\"}"));
    }
}
