package com.example.nexusauth.dto.password;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(@NotBlank String resetTicket,
                                   @NotBlank @Size(min = 8, max = 72) String newPassword) {}
