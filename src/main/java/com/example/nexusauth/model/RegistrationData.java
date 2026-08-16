package com.example.nexusauth.model;

import java.util.List;

import com.example.nexusauth.annotations.TelephoneList;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.UUID;
import org.hibernate.validator.constraints.br.CNPJ;

public record RegistrationData(
        @NotNull
        ProfileType type,

        @NotNull
        @Email
        String email,

        @NotNull
        String name,

        @TelephoneList
        List<String> phones,

        AddressData address,

        String profileImageUrl,

        @CNPJ
        String cnpj,

        @UUID
        Long planId,

        @NotNull
        AuthProvider provider,

        @NotNull
        String credential
) {}
