package com.example.nexusauth.model;

import jakarta.validation.constraints.NotNull;

public record AddressData(

        @NotNull
        String neighborhood,

        @NotNull
        String street,

        @NotNull
        String number,

        @NotNull
        String cep,

        @NotNull
        String city,

        @NotNull
        String state
) {}
