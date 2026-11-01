package com.example.nexusauth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

class OtpMailServiceTest {
    private HttpClient httpClient;
    private OtpMailService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        httpClient = mock(HttpClient.class);
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(201);
        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
                .thenReturn(response);
        service = new OtpMailService(httpClient, new ObjectMapper(), "xkeysib-test",
                "no-reply@example.com", "Ceris");
    }

    @Test
    void sendsRegistrationLayoutWithLogoAndConfiguredValidity() throws Exception {
        service.send("user@example.com", "012345", OtpMailService.Purpose.REGISTRATION, Duration.ofMinutes(15));

        HttpRequest request = sentRequest();
        JsonNode payload = payload(request);
        assertThat(request.uri().toString()).isEqualTo("https://api.brevo.com/v3/smtp/email");
        assertThat(request.headers().firstValue("api-key")).contains("xkeysib-test");
        assertThat(request.headers().firstValue("Content-Type")).contains("application/json");
        assertThat(payload.path("to").get(0).path("email").asText()).isEqualTo("user@example.com");
        assertThat(payload.path("subject").asText()).isEqualTo("Confirme seu e-mail · Ceris");
        assertThat(payload.path("htmlContent").asText())
                .contains("Confirme<br>seu e-mail.", "012345", ">15<span", " min</span>")
                .contains("https://res.cloudinary.com/dhtsty5gj/image/upload/v1793520048/ceris-logo-roxa.png")
                .contains("@media screen and (max-width: 640px)")
                .doesNotContain("{{", "<script", "<!--");
        assertThat(payload.path("textContent").asText()).contains("confirmar seu cadastro", "012345", "15 min");
    }

    @Test
    void sendsPasswordResetLayoutWithItsOwnValidity() throws Exception {
        service.send("user@example.com", "654321", OtpMailService.Purpose.PASSWORD_RESET, Duration.ofMinutes(10));

        JsonNode payload = payload(sentRequest());
        assertThat(payload.path("subject").asText()).isEqualTo("Recupere sua senha · Ceris");
        assertThat(payload.path("htmlContent").asText())
                .contains("Recupere<br>sua senha.", "654321", ">10<span")
                .doesNotContain("Confirme<br>seu e-mail.", "{{");
        assertThat(payload.path("textContent").asText()).contains("recuperar sua senha", "654321", "10 min");
    }

    @Test
    void preservesValidityThatIsNotAnEntireMinute() throws Exception {
        service.send("user@example.com", "123456", OtpMailService.Purpose.PASSWORD_RESET, Duration.ofSeconds(90));

        JsonNode payload = payload(sentRequest());
        assertThat(payload.path("htmlContent").asText()).contains(">90<span", " s</span>");
        assertThat(payload.path("textContent").asText()).contains("90 s");
    }

    private HttpRequest sentRequest() throws Exception {
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(request.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any());
        return request.getValue();
    }

    private JsonNode payload(HttpRequest request) throws Exception {
        HttpResponse.BodySubscriber<String> subscriber = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(ByteBuffer item) {
                subscriber.onNext(List.of(item));
            }

            @Override
            public void onError(Throwable throwable) {
                subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        });
        return new ObjectMapper().readTree(subscriber.getBody().toCompletableFuture().get(5, TimeUnit.SECONDS));
    }
}
