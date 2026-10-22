package com.example.nexusauth.geo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.geocoding")
public record GeocodingProperties(
        @DefaultValue("https://nominatim.openstreetmap.org") String baseUrl,
        @DefaultValue("NexUs-Auth/1.0") String userAgent
) {}
