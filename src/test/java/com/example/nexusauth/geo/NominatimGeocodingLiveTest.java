package com.example.nexusauth.geo;

import com.example.nexusauth.model.AddressData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "GEOCODING_LIVE_TEST", matches = "true")
class NominatimGeocodingLiveTest {
    @Test
    void resolvesPublicMaspAddressThroughProductionHttpClient() {
        var service = new NominatimGeocodingService(new GeocodingProperties(
                "https://nominatim.openstreetmap.org", "NexUs-Auth/1.0"));

        GeoPoint point = service.geocode(new AddressData(
                "Bela Vista", "Avenida Paulista", "1578", "01310200", "São Paulo", "SP")).orElseThrow();

        assertThat(point.latitude()).isBetween(new BigDecimal("-23.562"), new BigDecimal("-23.560"));
        assertThat(point.longitude()).isBetween(new BigDecimal("-46.657"), new BigDecimal("-46.654"));
    }
}
