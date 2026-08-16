package com.example.nexusauth.dto.registration;

import java.util.List;

import com.example.nexusauth.model.AddressData;
import com.example.nexusauth.model.AuthProvider;
import com.example.nexusauth.model.ProfileType;

public record RegistrationData(
        ProfileType type,
        String email,
        String name,
        List<String> phones,
        AddressData address,
        String profileImageUrl,
        String cnpj,
        Long planId,
        AuthProvider provider,
        String credential
) {}
