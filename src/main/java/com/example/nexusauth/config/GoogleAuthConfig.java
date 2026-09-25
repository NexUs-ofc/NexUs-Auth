package com.example.nexusauth.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Arrays;
import java.util.List;

@Configuration
public class GoogleAuthConfig {

    private static final Logger logger =
            LoggerFactory.getLogger(GoogleAuthConfig.class);

    @Bean
    GoogleIdTokenVerifier googleIdTokenVerifier(@Value("${app.google.client-ids:}") String clientIds) {

        List<String> audiences =
                audiences(clientIds);

        if (audiences.isEmpty()) {

            logger.error(
                    "Nenhum client ID do Google configurado em app.google.client-ids; "
                            + "a autenticação Google ficará indisponível"
            );

            return null;
        }

        logger.info(
                "GoogleIdTokenVerifier configurado com {} audiência(s): {}",
                audiences.size(),
                audiences
        );

        return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(audiences)
                .build();
    }

    static List<String> audiences(String clientIds) {

        if (clientIds == null || clientIds.isBlank()) {
            return List.of();
        }

        return Arrays.stream(clientIds.split(","))
                .map(String::trim)
                .filter(clientId -> !clientId.isEmpty())
                .distinct()
                .toList();
    }
}
