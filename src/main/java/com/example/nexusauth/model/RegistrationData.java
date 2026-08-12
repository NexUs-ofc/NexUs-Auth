package com.example.nexusauth.model;

import java.util.List;

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
