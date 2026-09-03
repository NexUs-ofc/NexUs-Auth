package com.example.nexusauth.dto.auth;

import com.example.nexusauth.dto.registration.GoogleRegistrationRequiredResponse;
import com.example.nexusauth.dto.session.SessionResponse;

public record GoogleAuthenticateResponse(boolean registrationRequired,
                                        SessionResponse session,
                                        GoogleRegistrationRequiredResponse registration) {}
