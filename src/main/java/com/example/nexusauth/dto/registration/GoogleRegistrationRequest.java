package com.example.nexusauth.dto.registration;

import com.example.nexusauth.dto.address.AddressRequest;
import com.example.nexusauth.model.ProfileType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record GoogleRegistrationRequest(
        @NotBlank String googleTicket,
        @NotNull ProfileType type,
        List<@Pattern(regexp = "\\+[1-9][0-9]{7,14}") String> phones,
        @Valid AddressRequest address,
        @Pattern(regexp = "\\d{14}") String cnpj,
        Long planId
) {}
