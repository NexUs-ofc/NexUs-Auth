package com.example.nexusauth.geo;

import com.example.nexusauth.model.AddressData;
import org.junit.jupiter.api.AfterEach;
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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DistanceMatrixGeocodingServiceTest {

    private static final String BASE_URL = "https://api.distancematrix.ai";
    private static final String API_KEY = "test-key+with&reserved=characters";
    private static final AddressData ADDRESS = new AddressData(
            "Bela Vista", "Avenida Paulista", "1578", "01310200", "São Paulo", "SP");
    private static final String LOCATION = "{\"geometry\":{\"location\":{\"lat\":-23.61,\"lng\":-46.77}}}";

    private MockRestServiceServer server;
    private DistanceMatrixGeocodingService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new DistanceMatrixGeocodingService(new GeocodingProperties(BASE_URL, API_KEY), builder);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"results", "result"})
    void sendsAccurateRequestWithEncodedAddressAndConfiguredKey(String resultsField) {
        server.expect(request -> {
            assertThat(request.getURI().getHost()).isEqualTo("api.distancematrix.ai");
            assertThat(request.getURI().getPath()).isEqualTo("/maps/api/geocode/json");
            assertThat(queryParameters(request.getURI().getRawQuery())).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "address", "Estrada São Francisco & A+B #1, 10, Jardim Henriqueta, "
                            + "Taboão da Serra - SP, 06753000, Brasil",
                    "key", API_KEY));
        }).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":\"OK\",\"" + resultsField + "\":[" + LOCATION + "]}",
                        MediaType.APPLICATION_JSON));

        GeoPoint point = service.geocode(new AddressData(" Jardim Henriqueta ", " Estrada São Francisco & A+B #1 ",
                " 10 ", " 06753000 ", " Taboão da Serra ", " sp ")).orElseThrow();

        assertThat(point).isEqualTo(new GeoPoint(new BigDecimal("-23.61"), new BigDecimal("-46.77")));
    }

    @Test
    void returnsEmptyOnlyWhenAddressHasNoMatch() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(
                "{\"status\":\"ZERO_RESULTS\",\"results\":[]}", MediaType.APPLICATION_JSON));

        assertThat(service.geocode(ADDRESS)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "REQUEST_DENIED", "OVER_QUERY_LIMIT", "OVER_DAILY_LIMIT", "INVALID_REQUEST", "UNKNOWN_ERROR"
    })
    void reportsProviderErrorsAsUnavailable(String status) {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(
                "{\"status\":\"" + status + "\",\"results\":[" + LOCATION + "]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.geocode(ADDRESS)).isInstanceOf(GeocodingUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "{broken}", "{\"status\":\"OK\"}", "{\"status\":\"OK\",\"results\":[]}",
        "{\"status\":\"OK\",\"results\":[null]}",
        "{\"status\":\"OK\",\"results\":[{}]}",
        "{\"status\":\"OK\",\"results\":[{\"geometry\":{}}]}",
        "{\"status\":\"OK\",\"results\":[{\"geometry\":{\"location\":{\"lat\":-23}}}]}"
    })
    void rejectsMalformedOrIncompleteResponse(String response) {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.geocode(ADDRESS)).isInstanceOf(GeocodingUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"lat\":91,\"lng\":0}", "{\"lat\":-91,\"lng\":0}",
        "{\"lat\":0,\"lng\":181}", "{\"lat\":0,\"lng\":-181}",
        "{\"lat\":null,\"lng\":0}", "{\"lat\":\"invalid\",\"lng\":0}"
    })
    void rejectsInvalidCoordinates(String coordinates) {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(
                "{\"status\":\"OK\",\"results\":[{\"geometry\":{\"location\":" + coordinates + "}}]}",
                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.geocode(ADDRESS)).isInstanceOf(GeocodingUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 429, 500, 503})
    void reportsHttpErrorsWithoutExposingKeyInException(int status) {
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() -> service.geocode(ADDRESS))
                .isInstanceOf(GeocodingUnavailableException.class)
                .hasNoCause()
                .hasMessageNotContaining(API_KEY);
    }

    @Test
    void reportsConnectionFailureAsUnavailable() {
        server.expect(method(HttpMethod.GET)).andRespond(withException(new IOException("Timeout")));

        assertThatThrownBy(() -> service.geocode(ADDRESS))
                .isInstanceOf(GeocodingUnavailableException.class).hasNoCause();
    }

    @Test
    void hidesApiKeyInConfigurationDescription() {
        assertThat(new GeocodingProperties(BASE_URL, API_KEY).toString()).doesNotContain(API_KEY);
    }

    @Test
    void reusesSuccessfulCoordinatesWithoutAnotherProviderCall() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(
                "{\"status\":\"OK\",\"results\":[" + LOCATION + "]}", MediaType.APPLICATION_JSON));

        GeoPoint first = service.geocode(ADDRESS).orElseThrow();

        assertThat(service.geocode(ADDRESS)).contains(first);
    }

    @Test
    void allowsRetryAfterAddressHasNoMatch() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(
                "{\"status\":\"ZERO_RESULTS\",\"results\":[]}", MediaType.APPLICATION_JSON));
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(
                "{\"status\":\"OK\",\"results\":[" + LOCATION + "]}", MediaType.APPLICATION_JSON));

        assertThat(service.geocode(ADDRESS)).isEmpty();
        assertThat(service.geocode(ADDRESS)).isPresent();
    }

    private Map<String, String> queryParameters(String rawQuery) {
        return Arrays.stream(rawQuery.split("&"))
                .map(parameter -> parameter.split("=", 2))
                .collect(Collectors.toMap(parameter -> decode(parameter[0]), parameter -> decode(parameter[1])));
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
