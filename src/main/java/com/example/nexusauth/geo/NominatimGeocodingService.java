package com.example.nexusauth.geo;

import com.example.nexusauth.model.AddressData;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class NominatimGeocodingService implements GeocodingService {
    private static final long REQUEST_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final int CACHE_LIMIT = 256;

    private final RestClient restClient;
    private final Map<AddressData, GeoPoint> cache = new LinkedHashMap<>();
    private long lastRequestNanos = System.nanoTime() - REQUEST_INTERVAL_NANOS;

    @Autowired
    public NominatimGeocodingService(GeocodingProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", properties.userAgent())
                .build();
    }

    NominatimGeocodingService(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public synchronized Optional<GeoPoint> geocode(AddressData address) {
        GeoPoint cached = cache.get(address);
        if (cached != null) {
            return Optional.of(cached);
        }
        waitForRequestSlot();
        try {
            NominatimResult[] results = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/search")
                            .queryParam("format", "jsonv2")
                            .queryParam("limit", 1)
                            .queryParam("countrycodes", "br")
                            .queryParam("street", address.number().trim() + " " + address.street().trim())
                            .queryParam("city", address.city().trim())
                            .queryParam("state", address.state().trim())
                            .build())
                    .retrieve()
                    .body(NominatimResult[].class);
            if (results == null || results.length == 0) {
                return Optional.empty();
            }
            NominatimResult result = results[0];
            if (result == null || result.lat() == null || result.lon() == null) {
                throw new IllegalArgumentException("Resposta de localizacao incompleta");
            }
            GeoPoint point = new GeoPoint(new BigDecimal(result.lat()), new BigDecimal(result.lon()));
            if (point.latitude().abs().compareTo(BigDecimal.valueOf(90)) > 0
                    || point.longitude().abs().compareTo(BigDecimal.valueOf(180)) > 0) {
                throw new IllegalArgumentException("Coordenadas de localizacao invalidas");
            }
            if (cache.size() >= CACHE_LIMIT) {
                cache.remove(cache.keySet().iterator().next());
            }
            cache.put(address, point);
            return Optional.of(point);
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new GeocodingUnavailableException(exception);
        }
    }

    private void waitForRequestSlot() {
        long remaining = REQUEST_INTERVAL_NANOS - (System.nanoTime() - lastRequestNanos);
        try {
            if (remaining > 0) {
                TimeUnit.NANOSECONDS.sleep(remaining);
            }
            lastRequestNanos = System.nanoTime();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GeocodingUnavailableException(exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NominatimResult(String lat, String lon) {}
}
