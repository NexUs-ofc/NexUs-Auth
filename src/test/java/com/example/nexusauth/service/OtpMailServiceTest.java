package com.example.nexusauth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import tools.jackson.databind.ObjectMapper;

class OtpMailServiceTest {
    @Test
    void sendsOtpThroughBrevoApi() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(201);
        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
                .thenReturn(response);
        OtpMailService service = new OtpMailService(httpClient, new ObjectMapper(), "xkeysib-test",
                "nexus.inter2026@gmail.com", "NexUs");

        service.send("user@example.com", "123456", "confirmar seu cadastro");

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(request.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any());
        assertThat(request.getValue().uri().toString()).isEqualTo("https://api.brevo.com/v3/smtp/email");
        assertThat(request.getValue().headers().firstValue("api-key")).contains("xkeysib-test");
        assertThat(request.getValue().headers().firstValue("Content-Type")).contains("application/json");
    }
}
