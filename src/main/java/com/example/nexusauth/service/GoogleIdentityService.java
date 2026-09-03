package com.example.nexusauth.service;

import com.example.nexusauth.model.AuthProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.security.GeneralSecurityException;

@Service
public class GoogleIdentityService {

    private static final Logger logger =
            LoggerFactory.getLogger(GoogleIdentityService.class);

    private final ObjectProvider<GoogleIdTokenVerifier> googleIdTokenVerifier;

    public GoogleIdentityService(
            ObjectProvider<GoogleIdTokenVerifier> googleIdTokenVerifier
    ) {
        this.googleIdTokenVerifier = googleIdTokenVerifier;
    }

    public Identity verify(String idToken) {

        logger.info(
                "Iniciando validação de identidade através do Google"
        );

        GoogleIdTokenVerifier verifier =
                googleIdTokenVerifier.getIfAvailable();

        if (verifier == null) {

            logger.error(
                    "GoogleIdTokenVerifier não está disponível para validação do token"
            );

            throw new GoogleUnavailableException();
        }

        logger.debug(
                "Instância GoogleIdTokenVerifier recuperada com sucesso"
        );

        GoogleIdToken token;

        try {

            token = verifier.verify(idToken);

        } catch (GeneralSecurityException | IOException e) {

            logger.warn(
                    "Falha na validação do token Google: {}",
                    e.getMessage()
            );

            throw new InvalidGoogleTokenException();
        }

        if (token == null) {

            logger.warn(
                    "Token Google inválido ou expirado"
            );

            throw new InvalidGoogleTokenException();
        }

        GoogleIdToken.Payload payload =
                token.getPayload();

        logger.debug(
                "Token Google validado com sucesso sub={}",
                payload.getSubject()
        );

        if (payload.getEmail() == null ||
                !Boolean.TRUE.equals(payload.getEmailVerified())) {

            logger.warn(
                    "Token Google rejeitado por email ausente ou não verificado sub={}"
                            + " emailPresente={} emailVerificado={}",
                    payload.getSubject(),
                    payload.getEmail() != null,
                    payload.getEmailVerified()
            );

            throw new UnverifiedEmailException();
        }

        Identity identity =
                new Identity(
                        payload.getSubject(),
                        payload.getEmail(),
                        (String) payload.get("name"),
                        (String) payload.get("picture"),
                        AuthProvider.GOOGLE
                );

        logger.info(
                "Identidade Google validada com sucesso sub={} provider={} email={}",
                identity.uid(),
                identity.provider(),
                identity.email()
        );

        return identity;
    }

    public record Identity(
            String uid,
            String email,
            String name,
            String picture,
            AuthProvider provider
    ) {}

    public static class GoogleUnavailableException
            extends RuntimeException {}

    public static class InvalidGoogleTokenException
            extends RuntimeException {}

    public static class UnverifiedEmailException
            extends RuntimeException {}
}
