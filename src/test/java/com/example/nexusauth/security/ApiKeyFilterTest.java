package com.example.nexusauth.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiKeyFilterTest {

    @Test
    void skipsCorsPreflightRequests() {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/google/authenticate");
        request.addHeader("Origin", "https://client.example.com");
        request.addHeader("Access-Control-Request-Method", "POST");

        ApiKeyFilter filter = new ApiKeyFilter("api-key");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }
}
