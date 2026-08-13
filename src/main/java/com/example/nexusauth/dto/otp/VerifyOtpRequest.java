package com.example.nexusauth.dto.otp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(@NotBlank String registrationId,
                               @NotBlank @Pattern(regexp = "\\d{6}") String otp) {}
