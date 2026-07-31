package com.example.nexusauth.service;

import com.example.nexusauth.model.AuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class FirebaseIdentityService {

    private static final Logger logger =
            LoggerFactory.getLogger(FirebaseIdentityService.class);

    private final ObjectProvider<FirebaseAuth> firebaseAuth;

    public FirebaseIdentityService(
            ObjectProvider<FirebaseAuth> firebaseAuth
    ) {
        this.firebaseAuth = firebaseAuth;
    }

    public Identity verify(String idToken) {

        logger.info(
                "Iniciando validação de identidade através do Firebase"
        );

        FirebaseAuth auth =
                firebaseAuth.getIfAvailable();

        if (auth == null) {

            logger.error(
                    "FirebaseAuth não está disponível para validação do token"
            );

            throw new FirebaseUnavailableException();
        }

        logger.debug(
                "Instância FirebaseAuth recuperada com sucesso"
        );

        try {

            FirebaseToken token =
                    auth.verifyIdToken(idToken);

            logger.debug(
                    "Token Firebase validado com sucesso uid={}",
                    token.getUid()
            );

            if (token.getEmail() == null ||
                    !token.isEmailVerified()) {

                logger.warn(
                        "Token Firebase rejeitado por email ausente ou não verificado uid={} emailPresente={} emailVerificado={}",
                        token.getUid(),
                        token.getEmail() != null,
                        token.isEmailVerified()
                );

                throw new UnverifiedEmailException();
            }

            Object firebaseClaim =
                    token.getClaims().get("firebase");

            String signInProvider =
                    firebaseClaim instanceof Map<?, ?> map
                            ? String.valueOf(
                            map.get("sign_in_provider")
                    )
                            : "";

            logger.debug(
                    "Provedor de autenticação Firebase identificado uid={} provider={}",
                    token.getUid(),
                    signInProvider
            );

            AuthProvider provider =
                    switch (signInProvider) {
                        case "google.com" ->
                                AuthProvider.GOOGLE;

                        case "microsoft.com" ->
                                AuthProvider.MICROSOFT;

                        default -> {

                            logger.warn(
                                    "Provedor Firebase não suportado uid={} provider={}",
                                    token.getUid(),
                                    signInProvider
                            );

                            throw new UnsupportedProviderException();
                        }
                    };

            Identity identity =
                    new Identity(
                            token.getUid(),
                            token.getEmail(),
                            token.getName(),
                            token.getPicture(),
                            provider
                    );

            logger.info(
                    "Identidade Firebase validada com sucesso uid={} provider={} email={}",
                    identity.uid(),
                    identity.provider(),
                    identity.email()
            );

            return identity;

        } catch (FirebaseAuthException e) {

            logger.warn(
                    "Falha na validação do token Firebase: {}",
                    e.getMessage()
            );

            throw new InvalidFirebaseTokenException();
        }
    }

    public record Identity(
            String uid,
            String email,
            String name,
            String picture,
            AuthProvider provider
    ) {}

    public static class FirebaseUnavailableException
            extends RuntimeException {}

    public static class InvalidFirebaseTokenException
            extends RuntimeException {}

    public static class UnverifiedEmailException
            extends RuntimeException {}

    public static class UnsupportedProviderException
            extends RuntimeException {}
}