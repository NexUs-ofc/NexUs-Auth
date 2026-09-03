package com.example.nexusauth.dto.registration;

import java.util.List;

public record GoogleRegistrationRequiredResponse(String googleTicket, String email, String name,
                                                 String profileImageUrl, List<String> requiredFields) {}
