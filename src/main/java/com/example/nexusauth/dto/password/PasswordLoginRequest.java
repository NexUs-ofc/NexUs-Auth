package com.example.nexusauth.dto.password;

import com.example.nexusauth.model.Channel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PasswordLoginRequest(@NotBlank @Email String email, @NotBlank String password,
                                   @NotNull Channel channel) {}
