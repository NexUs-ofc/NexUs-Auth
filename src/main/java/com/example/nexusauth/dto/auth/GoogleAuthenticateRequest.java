package com.example.nexusauth.dto.auth;

import com.example.nexusauth.model.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GoogleAuthenticateRequest(@NotBlank String idToken, @NotNull Channel channel) {}
