package com.example.nexusauth.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {

    @Bean
    InMemoryRateLimiter inMemoryRateLimiter(RateLimitProperties properties) {
        return new InMemoryRateLimiter(properties.maxEntries());
    }

    @Bean
    ClientIpResolver clientIpResolver(RateLimitProperties properties) {
        return new ClientIpResolver(properties.trustedProxies());
    }

    @Bean
    AuthRateLimitService authRateLimitService(InMemoryRateLimiter limiter, RateLimitProperties properties,
                                             ClientIpResolver clientIpResolver) {
        return new AuthRateLimitService(limiter, properties, clientIpResolver);
    }
}
