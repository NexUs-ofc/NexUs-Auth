package com.example.nexusauth.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Service
public class OtpMailService {
    private static final URI BREVO_EMAILS_URI = URI.create("https://api.brevo.com/v3/smtp/email");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String from;
    private final String fromName;

    @Autowired
    public OtpMailService(ObjectMapper mapper,
                          @Value("${app.mail.brevo-api-key}") String apiKey,
                          @Value("${app.mail.from}") String from,
                          @Value("${app.mail.from-name}") String fromName) {
        this(HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build(), mapper, apiKey, from, fromName);
    }

    OtpMailService(HttpClient httpClient, ObjectMapper mapper, String apiKey, String from, String fromName) {
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.from = from;
        this.fromName = fromName;
    }

    public void send(String email, String otp, String purpose) {
        HttpRequest request = HttpRequest.newBuilder(BREVO_EMAILS_URI)
                .timeout(REQUEST_TIMEOUT)
                .header("api-key", apiKey)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        json(new BrevoEmail(new Sender(from, fromName), List.of(new Recipient(email)),
                                "Código de confirmação",
                                "Seu código para " + purpose + " é " + otp + ". Ele expira em poucos minutos.")),
                        StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new EmailDeliveryException();
            }
        } catch (IOException exception) {
            throw new EmailDeliveryException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EmailDeliveryException(exception);
        }
    }

    private String json(BrevoEmail email) {
        try {
            return mapper.writeValueAsString(email);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Não foi possível preparar o e-mail", exception);
        }
    }

    private record BrevoEmail(Sender sender, List<Recipient> to, String subject, String textContent) {}
    private record Sender(String email, String name) {}
    private record Recipient(String email) {}

    public static class EmailDeliveryException extends RuntimeException {
        EmailDeliveryException() {
            super("Não foi possível enviar o código por e-mail");
        }
        EmailDeliveryException(Throwable cause) {
            super("Não foi possível enviar o código por e-mail", cause);
        }
    }
}
