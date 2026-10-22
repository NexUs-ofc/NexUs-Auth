package com.example.nexusauth.config;

import com.example.nexusauth.geo.GeocodingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AuthProperties.class, GeocodingProperties.class})
public class AuthConfiguration {
}
