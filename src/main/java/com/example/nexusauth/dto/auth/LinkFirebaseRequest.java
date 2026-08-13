package com.example.nexusauth.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LinkFirebaseRequest(@NotBlank String idToken) {}
