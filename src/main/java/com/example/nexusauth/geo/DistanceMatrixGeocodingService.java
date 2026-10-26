package com.example.nexusauth.geo;

import com.example.nexusauth.model.AddressData;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class DistanceMatrixGeocodingService implements GeocodingService {

    private static final int CACHE_LIMIT = 256;

    private final RestClient restClient;
    private final String apiKey;
    private final Map<AddressData, GeoPoint> cache = new LinkedHashMap<>();

    @Autowired
    public DistanceMatrixGeocodingService(GeocodingProperties properties) {
        this(properties, RestClient.builder().requestFactory(requestFactory()));
    }

    DistanceMatrixGeocodingService(GeocodingProperties properties, RestClient.Builder builder) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.apiKey = properties.apiKey();
    }

    @Override
    public synchronized Optional<GeoPoint> geocode(AddressData address) {
        GeoPoint cached = cache.get(address);
        if (cached != null) {
            return Optional.of(cached);
        }
        String query = "%s, %s, %s, %s - %s, %s, Brasil".formatted(
                address.street().trim(), address.number().trim(), address.neighborhood().trim(),
                address.city().trim(), address.state().trim().toUpperCase(Locale.ROOT), address.cep().trim());
        try {
            GeocodingResponse response = restClient.get()
                    .uri(builder -> builder.path("/maps/api/geocode/json")
                            .queryParam("address", "{address}")
                            .queryParam("key", "{key}")
                            .build(Map.of("address", query, "key", apiKey)))
                    .retrieve()
                    .body(GeocodingResponse.class);
            if (response != null && "ZERO_RESULTS".equals(response.status())) {
                return Optional.empty();
            }
            if (response == null || !"OK".equals(response.status())
                    || response.results() == null || response.results().isEmpty()) {
                throw new GeocodingUnavailableException();
            }
            GeoPoint point = Optional.ofNullable(response.results().get(0))
                    .map(GeocodingResult::geometry)
                    .map(Geometry::location)
                    .filter(location -> validCoordinate(location.lat(), 90) && validCoordinate(location.lng(), 180))
                    .map(location -> new GeoPoint(location.lat(), location.lng()))
                    .orElseThrow(GeocodingUnavailableException::new);
            if (cache.size() >= CACHE_LIMIT) {
                cache.remove(cache.keySet().iterator().next());
            }
            cache.put(address, point);
            return Optional.of(point);
        } catch (RestClientException exception) {
            throw new GeocodingUnavailableException();
        }
    }

    private boolean validCoordinate(BigDecimal value, int limit) {
        return value != null && value.abs().compareTo(BigDecimal.valueOf(limit)) <= 0;
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeocodingResponse(String status, @JsonAlias("result") List<GeocodingResult> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeocodingResult(Geometry geometry) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Geometry(Location location) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Location(BigDecimal lat, BigDecimal lng) {}
}
