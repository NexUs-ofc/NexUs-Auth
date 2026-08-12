package com.example.nexusauth.dto.auth;

import com.example.nexusauth.dto.registration.FirebaseRegistrationRequiredResponse;
import com.example.nexusauth.dto.session.SessionResponse;

public record FirebaseAuthenticateResponse(boolean registrationRequired,
                                          SessionResponse session,
                                          FirebaseRegistrationRequiredResponse registration) {}
