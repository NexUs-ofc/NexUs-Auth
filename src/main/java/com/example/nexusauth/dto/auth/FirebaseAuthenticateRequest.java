package com.example.nexusauth.dto.auth;

import com.example.nexusauth.model.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FirebaseAuthenticateRequest(@NotBlank String idToken, @NotNull Channel channel) {}
