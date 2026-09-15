package com.example.nexusauth.exception;

import com.example.nexusauth.service.AuthService;
import com.example.nexusauth.service.GoogleIdentityService;
import com.example.nexusauth.service.PendingFlowService;
import com.example.nexusauth.service.RefreshTokenService;
import com.example.nexusauth.service.SessionService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({AuthService.InvalidCredentialsException.class,
            RefreshTokenService.InvalidRefreshTokenException.class,
            SessionService.InvalidSessionException.class,
            PendingFlowService.InvalidOrExpiredOtpException.class,
            PendingFlowService.InvalidOrExpiredResetTicketException.class,
            GoogleIdentityService.InvalidGoogleTokenException.class})
    ResponseEntity<?> unauthorized(RuntimeException exception) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_AUTHENTICATION", "Credencial inválida ou expirada");
    }

    @ExceptionHandler({AuthService.ChannelForbiddenException.class, AuthService.ProfileUnavailableException.class})
    ResponseEntity<?> forbidden(RuntimeException exception) {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Perfil sem acesso a este canal");
    }

    @ExceptionHandler({AuthService.EmailAlreadyUsedException.class, AuthService.CnpjAlreadyUsedException.class,
            AuthService.IdentityAlreadyLinkedException.class,
            DataIntegrityViolationException.class})
    ResponseEntity<?> conflict(Exception exception) {
        return error(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", "E-mail, CNPJ ou identidade já está em uso");
    }

    @ExceptionHandler({AuthService.InvalidRegistrationException.class, AuthService.EmailMismatchException.class,
            GoogleIdentityService.UnverifiedEmailException.class,
            PendingFlowService.ExpiredRegistrationException.class})
    ResponseEntity<?> badRequest(RuntimeException exception) {
        String message = exception.getMessage() == null ? "Dados inválidos" : exception.getMessage();
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    @ExceptionHandler(GoogleIdentityService.GoogleUnavailableException.class)
    ResponseEntity<?> googleUnavailable(RuntimeException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_AUTH_UNAVAILABLE", "Autenticação Google não configurada");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = exception.getBindingResult().getFieldErrors().stream()
                .collect(java.util.stream.Collectors.toMap(error -> error.getField(),
                        error -> error.getDefaultMessage() == null ? "inválido" : error.getDefaultMessage(),
                        (first, ignored) -> first));
        return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_ERROR", "fields", fields,
                "timestamp", Instant.now().toString()));
    }

    private ResponseEntity<?> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message,
                "timestamp", Instant.now().toString()));
    }
}
