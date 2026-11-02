package com.example.nexusauth.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(@DefaultValue("100000") int maxEntries,
                                  @DefaultValue("120") int requestsPerMinute,
                                  @DefaultValue("12") int loginAttempts,
                                  @DefaultValue("5m") Duration loginWindow,
                                  @DefaultValue("1") int otpPerMinute,
                                  @DefaultValue("5") int otpPerQuarterHour,
                                  @DefaultValue("30") int refreshPerMinute,
                                  @DefaultValue("") String trustedProxies) {
    public RateLimitProperties {
        if (maxEntries < 1 || requestsPerMinute < 1 || loginAttempts < 1
                || otpPerMinute < 1 || otpPerQuarterHour < 1 || refreshPerMinute < 1
                || loginWindow == null || loginWindow.isNegative() || loginWindow.isZero()) {
            throw new IllegalArgumentException("Rate limits must be positive");
        }
    }
}
