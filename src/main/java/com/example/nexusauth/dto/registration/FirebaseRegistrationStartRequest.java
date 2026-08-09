package com.example.nexusauth.dto.registration;

import com.example.nexusauth.dto.address.AddressRequest;
import com.example.nexusauth.model.ProfileType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record FirebaseRegistrationStartRequest(
        @NotBlank String firebaseTicket,
        @NotNull ProfileType type,
        @Size(max = 150) String name,
        @NotEmpty List<@Pattern(regexp = "\\+[1-9][0-9]{7,14}") String> phones,
        @NotNull @Valid AddressRequest address,
        @Pattern(regexp = "\\d{14}") String cnpj,
        Long planId
) {}
