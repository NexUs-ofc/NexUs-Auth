package com.example.nexusauth.ratelimit;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryRateLimiterTest {

    private final AtomicLong now = new AtomicLong();
    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter(1000, now::get);

    @Test
    void limitsEachIdentityIndependentlyAndRoundsRetryAfterUp() {
        var first = quota("first", 2, 60);
        assertThat(limiter.consume(first)).isZero();
        assertThat(limiter.consume(first)).isZero();
        now.addAndGet(1_100_000_000L);
        assertThat(limiter.consume(first)).isEqualTo(59);
        assertThat(limiter.consume(quota("second", 2, 60))).isZero();
    }

    @Test
    void restoresTheQuotaAtTheExactBoundaryWithoutExtendingItOnRejection() {
        var quota = quota("profile", 1, 60);
        assertThat(limiter.consume(quota)).isZero();
        now.set(Duration.ofSeconds(59).toNanos());
        assertThat(limiter.consume(quota)).isEqualTo(1);
        now.set(Duration.ofSeconds(60).toNanos());
        assertThat(limiter.consume(quota)).isZero();
    }

    @Test
    void doesNotConsumeAnyQuotaWhenAnotherRuleRejectsTheRequest() {
        var general = quota("general", 2, 60);
        var writes = quota("write", 1, 60);
        assertThat(limiter.consume(general, writes)).isZero();
        assertThat(limiter.consume(general, writes)).isEqualTo(60);
        assertThat(limiter.consume(general)).isZero();
        assertThat(limiter.consume(general)).isEqualTo(60);
    }

    @Test
    void reportsTheLongestWaitWhenMultipleRulesAreExhausted() {
        var minute = quota("minute", 1, 60);
        var quarterHour = quota("quarter", 1, 900);
        assertThat(limiter.consume(minute, quarterHour)).isZero();
        assertThat(limiter.consume(minute, quarterHour)).isEqualTo(900);
    }

    @Test
    void doesNotEvictActiveCountersWhenMemoryIsFull() {
        var bounded = new InMemoryRateLimiter(1, now::get);
        assertThat(bounded.consume(quota("existing", 2, 60))).isZero();
        assertThat(bounded.consume(quota("new", 2, 60))).isEqualTo(60);
        assertThat(bounded.consume(quota("existing", 2, 60))).isZero();
        assertThat(bounded.consume(quota("existing", 2, 60))).isEqualTo(60);
        now.set(Duration.ofMinutes(1).toNanos());
        assertThat(bounded.consume(quota("new", 2, 60))).isZero();
    }

    @Test
    void removesExpiredEntriesInExpirationOrderAcrossDifferentWindows() {
        var bounded = new InMemoryRateLimiter(2, now::get);
        assertThat(bounded.consume(quota("long", 1, 900), quota("short", 1, 60))).isZero();
        now.set(Duration.ofMinutes(1).toNanos());
        assertThat(bounded.consume(quota("new", 1, 60))).isZero();
        assertThat(bounded.consume(quota("long", 1, 900))).isEqualTo(840);
    }

    @Test
    void enforcesTheLimitAcrossConcurrentRequests() throws Exception {
        var executor = Executors.newFixedThreadPool(12);
        try {
            List<Callable<Long>> requests = new ArrayList<>();
            for (int index = 0; index < 100; index++) {
                requests.add(() -> limiter.consume(quota("shared", 12, 300)));
            }
            int allowed = 0;
            for (var result : executor.invokeAll(requests)) {
                if (result.get() == 0) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(12);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new InMemoryRateLimiter(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> quota("key", 0, 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> quota("key", 1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private InMemoryRateLimiter.Quota quota(String key, int limit, int seconds) {
        return new InMemoryRateLimiter.Quota(key, limit, Duration.ofSeconds(seconds));
    }
}
