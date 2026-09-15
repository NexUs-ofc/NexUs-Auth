package com.example.nexusauth.dto.password;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyPasswordResetRequest(@NotBlank String resetId,
                                         @NotBlank @Pattern(regexp = "\\d{6}") String otp) {}
