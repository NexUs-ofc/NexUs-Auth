package com.example.nexusauth.controller;

import com.example.nexusauth.dto.auth.GoogleAuthenticateRequest;
import com.example.nexusauth.dto.auth.GoogleAuthenticateResponse;
import com.example.nexusauth.dto.auth.LinkGoogleRequest;
import com.example.nexusauth.dto.otp.VerifyOtpRequest;
import com.example.nexusauth.dto.password.ForgotPasswordRequest;
import com.example.nexusauth.dto.password.LinkPasswordRequest;
import com.example.nexusauth.dto.password.PasswordLoginRequest;
import com.example.nexusauth.dto.password.PasswordResetPendingResponse;
import com.example.nexusauth.dto.password.PasswordResetTicketResponse;
import com.example.nexusauth.dto.password.ResetPasswordRequest;
import com.example.nexusauth.dto.password.VerifyPasswordResetRequest;
import com.example.nexusauth.dto.registration.GoogleRegistrationRequest;
import com.example.nexusauth.dto.registration.PasswordRegistrationStartRequest;
import com.example.nexusauth.dto.registration.PendingResponse;
import com.example.nexusauth.dto.session.SessionResponse;
import com.example.nexusauth.dto.token.LogoutRequest;
import com.example.nexusauth.dto.token.RefreshRequest;
import com.example.nexusauth.ratelimit.AuthRateLimitService;
import com.example.nexusauth.service.AuthService;
import com.example.nexusauth.service.LogoutService;
import com.example.nexusauth.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticação")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final SessionService sessions;
    private final LogoutService logout;
    private final AuthRateLimitService rateLimits;
    private final Logger logger = LoggerFactory.getLogger(AuthController.class);

    public AuthController(AuthService auth, SessionService sessions, LogoutService logout,
                          AuthRateLimitService rateLimits) {
        this.auth = auth;
        this.sessions = sessions;
        this.logout = logout;
        this.rateLimits = rateLimits;

    }

    @Operation(summary = "Iniciar cadastro por senha",
            description = "Valida os dados e inicia o cadastro com OTP. HOUSEHOLD exige endereço; COMPANY exige "
                    + "CNPJ e plano.")
    @ApiResponse(responseCode = "202", description = "Solicitação aceita; verifique o código enviado",
            content = @Content(schema = @Schema(implementation = PendingResponse.class)))
    @PostMapping("/registrations/password/start")
    public ResponseEntity<PendingResponse> startPasswordRegistration(
            @RequestBody @Valid PasswordRegistrationStartRequest request) {
        rateLimits.checkOtpSend(request.email());

        logger.info("Tentativa de cadastro por {}", request.email());

        return ResponseEntity.accepted().body(
                new PendingResponse(
                        auth.startPasswordRegistration(request)
                )
        );
    }

    @Operation(summary = "Autenticar por senha",
            description = "Verifica e-mail, senha e acesso ao canal PLATFORM ou MOBILE e retorna os tokens da sessão.")
    @PostMapping("/login/password")
    public SessionResponse passwordLogin(@RequestBody @Valid PasswordLoginRequest request,
                                         HttpServletRequest servletRequest) {
        rateLimits.checkLogin(request.email(), servletRequest);

        logger.info("Tentativa de login por {} em {}", request.email(), request.channel());

        return sessionResponse(
                auth.passwordLogin(request)
        );
    }

    @Operation(summary = "Autenticar com Google",
            description = "Valida o ID token Google. Uma identidade sem cadastro recebe 428 e dados para "
                    + "completar o registro.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Identidade reconhecida; sessão emitida",
                content = @Content(schema = @Schema(implementation = GoogleAuthenticateResponse.class))),
        @ApiResponse(responseCode = "428", description = "Cadastro necessário; utilize o googleTicket retornado",
                content = @Content(schema = @Schema(implementation = GoogleAuthenticateResponse.class)))
    })
    @PostMapping("/google/authenticate")
    public ResponseEntity<GoogleAuthenticateResponse> googleAuthenticate(
            @RequestBody @Valid GoogleAuthenticateRequest request) {

        logger.info("Tentativa de login por autenticação Google com email");

        GoogleAuthenticateResponse response = auth.googleAuthenticate(request);

        return ResponseEntity.status(
                        response.registrationRequired() ? HttpStatus.PRECONDITION_REQUIRED : HttpStatus.OK
                )
                .body(response);
    }

    @Operation(summary = "Completar cadastro Google",
            description = "Conclui o cadastro com o googleTicket e os campos obrigatórios do tipo de perfil.")
    @PostMapping("/registrations/google")
    public SessionResponse registerGoogle(@RequestBody @Valid GoogleRegistrationRequest request) {

        logger.info("Tentativa de cadastro através do Google");

        return sessionResponse(
                auth.registerGoogle(request)
        );
    }

    @Operation(summary = "Confirmar cadastro por OTP",
            description = "Confirma o cadastro pendente pelo registrationId e OTP de seis dígitos e emite a sessão.")
    @PostMapping("/registrations/verify")
    public SessionResponse verifyGoogleRegistration(@RequestBody @Valid VerifyOtpRequest request) {

        logger.info("Verificação de duas etapas para cadastro realizada!");

        return sessionResponse(
                auth.verifyRegistration(request.registrationId(), request.otp())
        );
    }

    @Operation(summary = "Renovar sessão",
            description = "Recebe o refresh token no corpo e retorna novos tokens conforme a política de renovação.")
    @PostMapping("/token/refresh")
    public SessionResponse refresh(@RequestBody @Valid RefreshRequest request) {

        logger.info("Atualização de token, renova sessão", request.refreshToken());

        return sessionResponse(
                sessions.refresh(request.refreshToken()
                )
        );
    }

    @Operation(summary = "Encerrar sessão",
            description = "Exige API key e JWT; revoga o refresh token e registra o JWT na blacklist do Auth.")
    @ApiResponse(responseCode = "204", description = "Operação concluída", content = @Content)
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid LogoutRequest request
    ) {

        logger.info("Logout realizado com token", request.refreshToken());

        logout.logout(jwt, request.refreshToken());

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Iniciar recuperação de senha",
            description = "Recebe o e-mail e retorna um resetId para a confirmação do OTP.")
    @ApiResponse(responseCode = "202", description = "Solicitação aceita; verifique o código enviado",
            content = @Content(schema = @Schema(implementation = PasswordResetPendingResponse.class)))
    @PostMapping("/password/forgot")
    public ResponseEntity<PasswordResetPendingResponse> forgotPassword(
            @RequestBody @Valid ForgotPasswordRequest request) {
        rateLimits.checkOtpSend(request.email());

        logger.info("Tentativa de recuperação de senha para email", request.email());

        return ResponseEntity.accepted().body(
                new PasswordResetPendingResponse(
                        auth.startPasswordReset(request.email())
                ));
    }

    @Operation(summary = "Confirmar OTP de recuperação",
            description = "Recebe resetId e OTP de seis dígitos e retorna um resetTicket para redefinir a senha.")
    @PostMapping("/password/reset/verify")
    public ResponseEntity<PasswordResetTicketResponse> verifyPasswordReset(
            @RequestBody @Valid VerifyPasswordResetRequest request) {

        logger.info("Verificação de OTP para recuperação de senha resetId={}", request.resetId());

        return ResponseEntity.ok(
                new PasswordResetTicketResponse(
                        auth.verifyPasswordReset(request.resetId(), request.otp())
                ));
    }

    @Operation(summary = "Redefinir senha",
            description = "Recebe o resetTicket e a nova senha, com tamanho entre 8 e 72 caracteres.")
    @ApiResponse(responseCode = "204", description = "Operação concluída", content = @Content)
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {

        logger.info("Atualização de senha concluída com sucesso");

        auth.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Vincular identidade Google",
            description = "Exige API key e JWT; vincula a identidade do ID token Google ao perfil autenticado.")
    @ApiResponse(responseCode = "204", description = "Operação concluída", content = @Content)
    @PostMapping("/google/link")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> linkGoogle(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                             @RequestBody @Valid LinkGoogleRequest request) {

        logger.info("Criação de link Google para autenticação automática");

        auth.linkGoogle(Long.parseLong(jwt.getSubject()), request.idToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Vincular senha ao perfil",
            description = "Exige API key e JWT; define uma credencial de senha para o perfil autenticado.")
    @ApiResponse(responseCode = "204", description = "Operação concluída", content = @Content)
    @PostMapping("/password/link")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> linkPassword(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                              @RequestBody @Valid LinkPasswordRequest request) {

        logger.info("Criação de link de senha para autenticação por email e senha");

        auth.linkPassword(Long.parseLong(jwt.getSubject()), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private SessionResponse sessionResponse(SessionService.Session session) {
        return new SessionResponse(session.accessToken(), session.accessTokenExpiresAt(),
                session.refreshToken(), session.refreshTokenExpiresAt());
    }
}
