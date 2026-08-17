package com.example.nexusauth.dto.registration;

import com.example.nexusauth.dto.address.AddressRequest;
import com.example.nexusauth.model.ProfileType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PasswordRegistrationStartRequest(
        @NotNull ProfileType type,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 150) String name,
        List<@Pattern(regexp = "\\+[1-9][0-9]{7,14}") String> phones,
        @Valid AddressRequest address,
        @Size(max = 500) String profileImageUrl,
        @Pattern(regexp = "\\d{14}") String cnpj,
        Long planId
) {}
