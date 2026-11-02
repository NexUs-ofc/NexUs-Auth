package com.example.nexusauth.dto.password;

import com.example.nexusauth.model.Channel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PasswordLoginRequest(
        @Schema(description = "E-mail cadastrado", example = "pessoa@example.com")
        @NotBlank @Email String email,
        @Schema(description = "Senha do perfil", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank String password,
        @Schema(description = "Canal de acesso: PLATFORM ou MOBILE", example = "MOBILE")
        @NotNull Channel channel
) {}
