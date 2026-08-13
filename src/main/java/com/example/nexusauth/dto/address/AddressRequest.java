package com.example.nexusauth.dto.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 100) String neighborhood,
        @NotBlank @Size(max = 150) String street,
        @NotBlank @Size(max = 10) String number,
        @NotBlank @Pattern(regexp = "\\d{8}") String cep,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String state
) {}
