package com.example.nexusauth.ratelimit;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.LongSupplier;

public final class InMemoryRateLimiter {

    private final int maxEntries;
    private final LongSupplier clock;
    private final Map<String, Window> windows = new HashMap<>();
    private final PriorityQueue<Window> expirations = new PriorityQueue<>(Comparator.comparingLong(w -> w.expiresAt));

    public InMemoryRateLimiter(int maxEntries) {
        this(maxEntries, System::nanoTime);
    }

    InMemoryRateLimiter(int maxEntries, LongSupplier clock) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    public synchronized long consume(Quota... quotas) {
        long now = clock.getAsLong();
        while (!expirations.isEmpty() && expirations.peek().expiresAt - now <= 0) {
            Window expired = expirations.remove();
            windows.remove(expired.key);
        }

        long retryAfter = 0;
        int missing = 0;
        for (Quota quota : quotas) {
            Window window = windows.get(quota.key());
            if (window == null) {
                missing++;
            } else if (window.requests >= quota.limit()) {
                retryAfter = Math.max(retryAfter, secondsUntil(window.expiresAt, now));
            }
        }
        if (retryAfter > 0) {
            return retryAfter;
        }
        if (missing > maxEntries - windows.size()) {
            return expirations.isEmpty() ? 1 : secondsUntil(expirations.peek().expiresAt, now);
        }

        for (Quota quota : quotas) {
            Window window = windows.get(quota.key());
            if (window == null) {
                window = new Window(quota.key(), now + quota.window().toNanos());
                windows.put(quota.key(), window);
                expirations.add(window);
            }
            window.requests++;
        }
        return 0;
    }

    private long secondsUntil(long expiresAt, long now) {
        return Math.max(1, (expiresAt - now + 999_999_999L) / 1_000_000_000L);
    }

    public record Quota(String key, int limit, Duration window) {
        public Quota {
            if (key == null || limit < 1 || window == null || window.isZero() || window.isNegative()) {
                throw new IllegalArgumentException("Invalid rate limit quota");
            }
        }
    }

    private static final class Window {
        private final String key;
        private final long expiresAt;
        private int requests;

        private Window(String key, long expiresAt) {
            this.key = key;
            this.expiresAt = expiresAt;
        }
    }
}
