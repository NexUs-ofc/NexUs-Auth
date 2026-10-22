package com.example.nexusauth.exception;

import com.example.nexusauth.geo.GeocodingUnavailableException;
import com.example.nexusauth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GeocodingExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void returnsRetryableServiceUnavailableForProviderFailure() throws Exception {
        mvc.perform(get("/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GEOCODING_UNAVAILABLE"));
    }

    @Test
    void returnsBadRequestForUnmatchedAddress() throws Exception {
        mvc.perform(get("/not-found"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @RestController
    static class FailureController {
        @GetMapping("/unavailable")
        public void unavailable() {
            throw new GeocodingUnavailableException(new RuntimeException("provider failure"));
        }

        @GetMapping("/not-found")
        public void notFound() {
            throw new AuthService.InvalidRegistrationException("Endereco nao encontrado");
        }
    }
}
