package com.example.nexusauth.controller;

import com.example.nexusauth.dto.AuthDtos;
import com.example.nexusauth.dto.SessionResponse;
import com.example.nexusauth.service.AuthService;
import com.example.nexusauth.service.LogoutService;
import com.example.nexusauth.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final SessionService sessions;
    private final LogoutService logout;
    private final Logger logger;

    public AuthController(AuthService auth, SessionService sessions, LogoutService logout, Logger logger) {
        this.auth = auth;
        this.sessions = sessions;
        this.logout = logout;
        this.logger = LoggerFactory.getLogger(AuthController.class);
    }

    @PostMapping("/registrations/password/start")
    public ResponseEntity<AuthDtos.PendingResponse> startPasswordRegistration(
            @RequestBody @Valid AuthDtos.PasswordRegistrationStartRequest request) {

        logger.info("Tentativa de cadastro por {}", request.email());

        return ResponseEntity.accepted().body(
                new AuthDtos.PendingResponse(
                        auth.startPasswordRegistration(request)
                )
        );
    }

    @PostMapping("/registrations/password/verify")
    public SessionResponse verifyPasswordRegistration(@RequestBody @Valid AuthDtos.VerifyOtpRequest request) {

        logger.info("Verificação de duas etapas para cadastro realizada");

        return sessionResponse(
                auth.verifyRegistration(request.registrationId(), request.otp())
        );
    }

    @PostMapping("/login/password")
    public SessionResponse passwordLogin(@RequestBody @Valid AuthDtos.PasswordLoginRequest request) {

        logger.info("Tentativa de login por {} em {}", request.email(), request.channel());

        return sessionResponse(
                auth.passwordLogin(request)
        );
    }

    @PostMapping("/firebase/authenticate")
    public ResponseEntity<AuthDtos.FirebaseAuthenticateResponse> firebaseAuthenticate(
            @RequestBody @Valid AuthDtos.FirebaseAuthenticateRequest request) {

        logger.info("Tentativa de login por autenticação firebase com email");

        AuthDtos.FirebaseAuthenticateResponse response = auth.firebaseAuthenticate(request);

        return ResponseEntity.status(
                response.registrationRequired() ? HttpStatus.PRECONDITION_REQUIRED : HttpStatus.OK
                )
                .body(response);
    }

    @PostMapping("/registrations/firebase/start")
    public ResponseEntity<AuthDtos.PendingResponse> startFirebaseRegistration(
            @RequestBody @Valid AuthDtos.FirebaseRegistrationStartRequest request) {

        logger.info("Tentativa de cadastro pelo ticket {}", request.firebaseTicket());

        return ResponseEntity.accepted().body(
                new AuthDtos.PendingResponse(
                        auth.startFirebaseRegistration(request)
                )
        );
    }

    @PostMapping("/registrations/firebase/verify")
    public SessionResponse verifyFirebaseRegistration(@RequestBody @Valid AuthDtos.VerifyOtpRequest request) {

        logger.info("Verificação de duas etapas para cadastro por firebase realizada!");

        return sessionResponse(
                auth.verifyRegistration(request.registrationId(), request.otp())
        );
    }

    @PostMapping("/token/refresh")
    public SessionResponse refresh(@RequestBody @Valid AuthDtos.RefreshRequest request) {

        logger.info("Atualização de token {}, renova sessão", request.refreshToken());

        return sessionResponse(
                sessions.refresh(request.refreshToken()
                )
        );
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid AuthDtos.LogoutRequest request
    ) {

        logger.info("Logout realizado com token {}", request.refreshToken());

        logout.logout(jwt, request.refreshToken());

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<AuthDtos.PasswordResetPendingResponse> forgotPassword(
            @RequestBody @Valid AuthDtos.ForgotPasswordRequest request) {

        logger.info("Tentativa de recuperação de senha para email {}", request.email());

        return ResponseEntity.accepted().body(
                new AuthDtos.PasswordResetPendingResponse(
                        auth.startPasswordReset(request.email())
                ));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@RequestBody @Valid AuthDtos.ResetPasswordRequest request) {

        logger.info("Atualização de senha com ID {} concluída!", request.resetId());

        auth.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/firebase/link")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> linkFirebase(@AuthenticationPrincipal Jwt jwt,
                                             @RequestBody @Valid AuthDtos.LinkFirebaseRequest request) {

        logger.info("Criação de link firebase para autenticação automática");

        auth.linkFirebase(Long.parseLong(jwt.getSubject()), request.idToken());
        return ResponseEntity.noContent().build();
    }

    private SessionResponse sessionResponse(SessionService.Session session) {
        return new SessionResponse(session.accessToken(), session.accessTokenExpiresAt(),
                session.refreshToken(), session.refreshTokenExpiresAt());
    }
}
