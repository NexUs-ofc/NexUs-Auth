package com.example.nexusauth.geo;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.geocoding")
public record GeocodingProperties(
        @NotBlank @DefaultValue("https://api.distancematrix.ai") String baseUrl,
        @NotBlank String apiKey
) {
    @Override
    public String toString() {
        return "GeocodingProperties[baseUrl=" + baseUrl + ", apiKey=***]";
    }
}
