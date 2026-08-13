package com.example.nexusauth.dto.registration;

import java.util.List;

public record FirebaseRegistrationRequiredResponse(String firebaseTicket, String email, String name,
                                                   String profileImageUrl, List<String> requiredFields) {}
