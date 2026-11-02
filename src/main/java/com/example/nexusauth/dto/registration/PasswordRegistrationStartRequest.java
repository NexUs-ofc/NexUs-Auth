package com.example.nexusauth.dto.registration;

import com.example.nexusauth.dto.address.AddressRequest;
import com.example.nexusauth.model.ProfileType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PasswordRegistrationStartRequest(
        @Schema(description = "Tipo permitido no cadastro público", allowableValues = {"HOUSEHOLD", "COMPANY"})
        @NotNull ProfileType type,
        @NotBlank @Email @Size(max = 255) String email,
        @Schema(description = "Senha de 8 a 72 caracteres", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 150) String name,
        List<@Pattern(regexp = "\\+[1-9][0-9]{7,14}") String> phones,
        @Schema(description = "Endereço obrigatório para HOUSEHOLD")
        @Valid AddressRequest address,
        @Size(max = 500) String profileImageUrl,
        @Schema(description = "CNPJ de 14 dígitos, obrigatório para COMPANY", example = "12345678000195")
        @Pattern(regexp = "\\d{14}") String cnpj,
        @Schema(description = "Plano ativo, obrigatório para COMPANY", example = "1") Long planId
) {}
