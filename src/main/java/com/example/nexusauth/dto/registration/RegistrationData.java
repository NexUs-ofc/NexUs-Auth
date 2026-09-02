package com.example.nexusauth.dto.registration;

import com.example.nexusauth.annotations.TelephoneList;
import com.example.nexusauth.model.AddressData;
import com.example.nexusauth.model.AuthProvider;
import com.example.nexusauth.model.ProfileType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.UUID;
import org.hibernate.validator.constraints.br.CNPJ;
import java.util.List;

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
) {

}