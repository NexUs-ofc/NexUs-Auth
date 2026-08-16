package com.example.nexusauth.controller;

import com.example.nexusauth.dto.auth.FirebaseAuthenticateRequest;
import com.example.nexusauth.dto.auth.FirebaseAuthenticateResponse;
import com.example.nexusauth.dto.auth.LinkFirebaseRequest;
import com.example.nexusauth.dto.otp.VerifyOtpRequest;
import com.example.nexusauth.dto.password.ForgotPasswordRequest;
import com.example.nexusauth.dto.password.PasswordLoginRequest;
import com.example.nexusauth.dto.password.PasswordResetPendingResponse;
import com.example.nexusauth.dto.password.ResetPasswordRequest;
import com.example.nexusauth.dto.registration.FirebaseRegistrationStartRequest;
import com.example.nexusauth.dto.registration.PasswordRegistrationStartRequest;
import com.example.nexusauth.dto.registration.PendingResponse;
import com.example.nexusauth.dto.session.SessionResponse;
import com.example.nexusauth.dto.token.LogoutRequest;
import com.example.nexusauth.dto.token.RefreshRequest;
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
    private final Logger logger = LoggerFactory.getLogger(AuthController.class);

    public AuthController(AuthService auth, SessionService sessions, LogoutService logout) {
        this.auth = auth;
        this.sessions = sessions;
        this.logout = logout;

    }

    @PostMapping("/registrations/password/start")
    public ResponseEntity<PendingResponse> startPasswordRegistration(
            @RequestBody @Valid PasswordRegistrationStartRequest request) {

        logger.info("Tentativa de cadastro por {}", request.email());

        return ResponseEntity.accepted().body(
                new PendingResponse(
                        auth.startPasswordRegistration(request)
                )
        );
    }

    @PostMapping("/registrations/password/verify")
    public SessionResponse verifyPasswordRegistration(@RequestBody @Valid VerifyOtpRequest request) {

        logger.info("Verificação de duas etapas para cadastro realizada");

        return sessionResponse(
                auth.verifyRegistration(request.registrationId(), request.otp())
        );
    }

    @PostMapping("/login/password")
    public SessionResponse passwordLogin(@RequestBody @Valid PasswordLoginRequest request) {

        logger.info("Tentativa de login por {} em {}", request.email(), request.channel());

        return sessionResponse(
                auth.passwordLogin(request)
        );
    }

    @PostMapping("/firebase/authenticate")
    public ResponseEntity<FirebaseAuthenticateResponse> firebaseAuthenticate(
            @RequestBody @Valid FirebaseAuthenticateRequest request) {

        logger.info("Tentativa de login por autenticação firebase com email");

        FirebaseAuthenticateResponse response = auth.firebaseAuthenticate(request);

        return ResponseEntity.status(
                        response.registrationRequired() ? HttpStatus.PRECONDITION_REQUIRED : HttpStatus.OK
                )
                .body(response);
    }

    @PostMapping("/registrations/firebase/start")
    public ResponseEntity<PendingResponse> startFirebaseRegistration(
            @RequestBody @Valid FirebaseRegistrationStartRequest request) {

        logger.info("Tentativa de cadastro pelo ticket", request.firebaseTicket());

        return ResponseEntity.accepted().body(
                new PendingResponse(
                        auth.startFirebaseRegistration(request)
                )
        );
    }

    @PostMapping("/registrations/firebase/verify")
    public SessionResponse verifyFirebaseRegistration(@RequestBody @Valid VerifyOtpRequest request) {

        logger.info("Verificação de duas etapas para cadastro por firebase realizada!");

        return sessionResponse(
                auth.verifyRegistration(request.registrationId(), request.otp())
        );
    }

    @PostMapping("/token/refresh")
    public SessionResponse refresh(@RequestBody @Valid RefreshRequest request) {

        logger.info("Atualização de token, renova sessão", request.refreshToken());

        return sessionResponse(
                sessions.refresh(request.refreshToken()
                )
        );
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid LogoutRequest request
    ) {

        logger.info("Logout realizado com token", request.refreshToken());

        logout.logout(jwt, request.refreshToken());

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<PasswordResetPendingResponse> forgotPassword(
            @RequestBody @Valid ForgotPasswordRequest request) {

        logger.info("Tentativa de recuperação de senha para email", request.email());

        return ResponseEntity.accepted().body(
                new PasswordResetPendingResponse(
                        auth.startPasswordReset(request.email())
                ));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {

        logger.info("Atualização de senha com concluída!", request.resetId());

        auth.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/firebase/link")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> linkFirebase(@AuthenticationPrincipal Jwt jwt,
                                             @RequestBody @Valid LinkFirebaseRequest request) {

        logger.info("Criação de link firebase para autenticação automática");

        auth.linkFirebase(Long.parseLong(jwt.getSubject()), request.idToken());
        return ResponseEntity.noContent().build();
    }

    private SessionResponse sessionResponse(SessionService.Session session) {
        return new SessionResponse(session.accessToken(), session.accessTokenExpiresAt(),
                session.refreshToken(), session.refreshTokenExpiresAt());
    }
}