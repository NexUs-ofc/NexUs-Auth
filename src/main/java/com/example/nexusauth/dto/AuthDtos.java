package com.example.nexusauth.dto;

import com.example.nexusauth.model.Channel;
import com.example.nexusauth.model.ProfileType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AuthDtos {
    private AuthDtos() {}

    public record AddressRequest(
            @NotBlank @Size(max = 100) String neighborhood,
            @NotBlank @Size(max = 150) String street,
            @NotBlank @Size(max = 10) String number,
            @NotBlank @Pattern(regexp = "\\d{8}") String cep,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String state
    ) {}

    public record PasswordRegistrationStartRequest(
            @NotNull ProfileType type,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 150) String name,
            @NotEmpty List<@Pattern(regexp = "\\+[1-9][0-9]{7,14}") String> phones,
            @NotNull @Valid AddressRequest address,
            @Size(max = 500) String profileImageUrl,
            @Pattern(regexp = "\\d{14}") String cnpj,
            Long planId
    ) {}

    public record VerifyOtpRequest(@NotBlank String registrationId,
                                   @NotBlank @Pattern(regexp = "\\d{6}") String otp) {}

    public record PasswordLoginRequest(@NotBlank @Email String email, @NotBlank String password,
                                       @NotNull Channel channel) {}

    public record FirebaseAuthenticateRequest(@NotBlank String idToken, @NotNull Channel channel) {}

    public record FirebaseRegistrationStartRequest(
            @NotBlank String firebaseTicket,
            @NotNull ProfileType type,
            @Size(max = 150) String name,
            @NotEmpty List<@Pattern(regexp = "\\+[1-9][0-9]{7,14}") String> phones,
            @NotNull @Valid AddressRequest address,
            @Pattern(regexp = "\\d{14}") String cnpj,
            Long planId
    ) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record LogoutRequest(@NotBlank String refreshToken) {}
    public record ForgotPasswordRequest(@NotBlank @Email String email) {}
    public record ResetPasswordRequest(@NotBlank String resetId,
                                       @NotBlank @Pattern(regexp = "\\d{6}") String otp,
                                       @NotBlank @Size(min = 8, max = 72) String newPassword) {}
    public record LinkFirebaseRequest(@NotBlank String idToken) {}

    public record PendingResponse(String registrationId) {}
    public record PasswordResetPendingResponse(String resetId) {}
    public record FirebaseRegistrationRequiredResponse(String firebaseTicket, String email, String name,
                                                       String profileImageUrl, List<String> requiredFields) {}
    public record FirebaseAuthenticateResponse(boolean registrationRequired,
                                               SessionResponse session,
                                               FirebaseRegistrationRequiredResponse registration) {}
}
