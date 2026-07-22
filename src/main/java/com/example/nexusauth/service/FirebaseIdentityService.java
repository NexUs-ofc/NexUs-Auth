package com.example.nexusauth.service;

import com.example.nexusauth.model.AuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class FirebaseIdentityService {
    private final ObjectProvider<FirebaseAuth> firebaseAuth;

    public FirebaseIdentityService(ObjectProvider<FirebaseAuth> firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    public Identity verify(String idToken) {
        FirebaseAuth auth = firebaseAuth.getIfAvailable();
        if (auth == null) throw new FirebaseUnavailableException();
        try {
            FirebaseToken token = auth.verifyIdToken(idToken);
            if (token.getEmail() == null || !token.isEmailVerified()) throw new UnverifiedEmailException();
            Object firebaseClaim = token.getClaims().get("firebase");
            String signInProvider = firebaseClaim instanceof Map<?, ?> map
                    ? String.valueOf(map.get("sign_in_provider")) : "";
            AuthProvider provider = switch (signInProvider) {
                case "google.com" -> AuthProvider.GOOGLE;
                case "microsoft.com" -> AuthProvider.MICROSOFT;
                default -> throw new UnsupportedProviderException();
            };
            return new Identity(token.getUid(), token.getEmail(), token.getName(), token.getPicture(), provider);
        } catch (FirebaseAuthException e) {
            throw new InvalidFirebaseTokenException();
        }
    }

    public record Identity(String uid, String email, String name, String picture, AuthProvider provider) {}
    public static class FirebaseUnavailableException extends RuntimeException {}
    public static class InvalidFirebaseTokenException extends RuntimeException {}
    public static class UnverifiedEmailException extends RuntimeException {}
    public static class UnsupportedProviderException extends RuntimeException {}
}
