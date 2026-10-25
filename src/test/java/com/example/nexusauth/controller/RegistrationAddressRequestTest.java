package com.example.nexusauth.controller;

import com.example.nexusauth.dto.address.AddressRequest;
import com.example.nexusauth.dto.registration.GoogleRegistrationRequest;
import com.example.nexusauth.dto.registration.PasswordRegistrationStartRequest;
import com.example.nexusauth.exception.ApiExceptionHandler;
import com.example.nexusauth.service.AuthService;
import com.example.nexusauth.service.LogoutService;
import com.example.nexusauth.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistrationAddressRequestTest {

    private static final String ADDRESS = """
            {"neighborhood":"Jardim Henriqueta","street":"Estrada São Francisco","number":"10",
             "cep":"06753000","city":"Taboão da Serra","state":"SP"}
            """;

    private AuthService auth;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        auth = mock(AuthService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth, mock(SessionService.class),
                        mock(LogoutService.class)))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void acceptsPasswordRegistrationWithPostalAddressAndNoCoordinates() throws Exception {
        when(auth.startPasswordRegistration(any())).thenReturn("registration-id");

        mvc.perform(post("/api/auth/registrations/password/start").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"HOUSEHOLD\",\"email\":\"user@example.com\",\"password\":\"password123\","
                                + "\"name\":\"User\",\"address\":" + ADDRESS + "}"))
                .andExpect(status().isAccepted());

        ArgumentCaptor<PasswordRegistrationStartRequest> request = ArgumentCaptor.forClass(
                PasswordRegistrationStartRequest.class);
        verify(auth).startPasswordRegistration(request.capture());
        assertThat(request.getValue().address()).isEqualTo(expectedAddress());
    }

    @Test
    void acceptsGoogleRegistrationWithPostalAddressAndNoCoordinates() throws Exception {
        when(auth.registerGoogle(any())).thenReturn(mock(SessionService.Session.class));

        mvc.perform(post("/api/auth/registrations/google").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"HOUSEHOLD\",\"googleTicket\":\"ticket\",\"address\":" + ADDRESS + "}"))
                .andExpect(status().isOk());

        ArgumentCaptor<GoogleRegistrationRequest> request = ArgumentCaptor.forClass(GoogleRegistrationRequest.class);
        verify(auth).registerGoogle(request.capture());
        assertThat(request.getValue().address()).isEqualTo(expectedAddress());
    }

    private AddressRequest expectedAddress() {
        return new AddressRequest("Jardim Henriqueta", "Estrada São Francisco", "10", "06753000",
                "Taboão da Serra", "SP");
    }
}
