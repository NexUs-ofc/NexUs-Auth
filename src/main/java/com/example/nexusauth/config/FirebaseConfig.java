package com.example.nexusauth.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import java.io.FileInputStream;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FirebaseConfig {
    @Bean
    @ConditionalOnProperty(name = "app.firebase.enabled", havingValue = "true")
    FirebaseAuth firebaseAuth(org.springframework.core.env.Environment environment) throws IOException {
        String path = environment.getRequiredProperty("app.firebase.credentials-path");
        if (path.isBlank()) throw new IllegalStateException("FIREBASE_CREDENTIALS_PATH não configurado");
        try (FileInputStream credentials = new FileInputStream(path)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials)).build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
            return FirebaseAuth.getInstance(app);
        }
    }
}
