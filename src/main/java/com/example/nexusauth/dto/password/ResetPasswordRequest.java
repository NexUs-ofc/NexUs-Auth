package com.example.nexusauth.dto.password;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(@NotBlank String resetId,
                                   @NotBlank @Pattern(regexp = "\\d{6}") String otp,
                                   @NotBlank @Size(min = 8, max = 72) String newPassword) {}
