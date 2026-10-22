package com.example.nexusauth.geo;

import com.example.nexusauth.model.AddressData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NominatimGeocodingServiceTest {
    private static final AddressData ADDRESS = new AddressData(
            "Bela Vista", "Avenida Paulista", "1578", "01310200", "São Paulo", "SP");
    private static final String RESULT = """
            [{"lat":"-23.5614961","lon":"-46.6559677","display_name":"MASP"}]
            """;

    private MockRestServiceServer server;
    private NominatimGeocodingService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://geocoding.example");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new NominatimGeocodingService(builder.build());
    }

    @Test
    void sendsStructuredBrazilianAddressAndReadsCoordinates() {
        server.expect(queryParam("street", "1578%20Avenida%20Paulista"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("city", "S%C3%A3o%20Paulo"))
                .andExpect(queryParam("state", "SP"))
                .andExpect(queryParam("countrycodes", "br"))
                .andExpect(queryParam("format", "jsonv2"))
                .andExpect(queryParam("limit", "1"))
                .andRespond(withSuccess(RESULT, MediaType.APPLICATION_JSON));

        GeoPoint point = service.geocode(ADDRESS).orElseThrow();

        assertThat(point.latitude()).isEqualByComparingTo(new BigDecimal("-23.5614961"));
        assertThat(point.longitude()).isEqualByComparingTo(new BigDecimal("-46.6559677"));
        server.verify();
    }

    @Test
    void returnsEmptyWhenAddressIsNotFound() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(service.geocode(ADDRESS)).isEmpty();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 429, 500, 503})
    void reportsProviderHttpErrorsAsUnavailable(int status) {
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() -> service.geocode(ADDRESS)).isInstanceOf(GeocodingUnavailableException.class);
        server.verify();
    }

    @Test
    void reportsConnectionFailureAsUnavailable() {
        server.expect(method(HttpMethod.GET)).andRespond(withException(new IOException("Connection timed out")));

        assertThatThrownBy(() -> service.geocode(ADDRESS)).isInstanceOf(GeocodingUnavailableException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "[{}]",
            "[null]",
            "[{\"lat\":\"invalid\",\"lon\":\"-46.6\"}]",
            "[{\"lat\":\"91\",\"lon\":\"-46.6\"}]",
            "[{\"lat\":\"-23.5\",\"lon\":\"181\"}]"
    })
    void rejectsMalformedOrInvalidCoordinates(String response) {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.geocode(ADDRESS)).isInstanceOf(GeocodingUnavailableException.class);
        server.verify();
    }

    @Test
    void reusesSuccessfulResultWithoutAnotherProviderCall() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(RESULT, MediaType.APPLICATION_JSON));

        GeoPoint first = service.geocode(ADDRESS).orElseThrow();
        GeoPoint second = service.geocode(ADDRESS).orElseThrow();

        assertThat(second).isEqualTo(first);
        server.verify();
    }

    @Test
    void spacesRequestsToRespectProviderLimit() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        long start = System.nanoTime();
        service.geocode(ADDRESS);
        service.geocode(ADDRESS);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofSeconds(1));
        server.verify();
    }
}
