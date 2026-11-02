package com.example.nexusauth.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRateLimitServiceTest {

    private final AtomicLong now = new AtomicLong();
    private final RateLimitProperties properties = new RateLimitProperties(1000, 120, 12,
            Duration.ofMinutes(5), 1, 5, 30, "");
    private final AuthRateLimitService limits = new AuthRateLimitService(
            new InMemoryRateLimiter(properties.maxEntries(), now::get), properties, new ClientIpResolver(""));

    @Test
    void allowsTwelveLoginsPerEmailAndIpAndRestoresThemAfterFiveMinutes() {
        var request = request("203.0.113.10");
        for (int index = 0; index < 12; index++) {
            limits.checkLogin("User@Example.com", request);
        }
        assertThatThrownBy(() -> limits.checkLogin(" user@example.com ", request))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.retryAfter()).isEqualTo(300));
        assertThatCode(() -> limits.checkLogin("other@example.com", request)).doesNotThrowAnyException();
        assertThatCode(() -> limits.checkLogin("user@example.com", request("203.0.113.11")))
                .doesNotThrowAnyException();
        now.set(Duration.ofMinutes(5).toNanos());
        assertThatCode(() -> limits.checkLogin("user@example.com", request)).doesNotThrowAnyException();
    }

    @Test
    void limitsOtpSendsToOnePerMinuteAndFivePerQuarterHour() {
        for (int index = 0; index < 5; index++) {
            limits.checkOtpSend("user@example.com");
            assertThatThrownBy(() -> limits.checkOtpSend("USER@example.com"))
                    .isInstanceOf(RateLimitExceededException.class);
            now.addAndGet(Duration.ofMinutes(1).toNanos());
        }
        assertThatThrownBy(() -> limits.checkOtpSend("user@example.com"))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.retryAfter()).isEqualTo(600));
        assertThatCode(() -> limits.checkOtpSend("other@example.com")).doesNotThrowAnyException();
        now.set(Duration.ofMinutes(15).toNanos());
        assertThatCode(() -> limits.checkOtpSend("user@example.com")).doesNotThrowAnyException();
    }

    @Test
    void appliesBothTheRefreshAndGeneralIpLimits() {
        var request = request("203.0.113.10");
        for (int index = 0; index < 30; index++) {
            limits.checkRequest(request, true);
        }
        assertThatThrownBy(() -> limits.checkRequest(request, true)).isInstanceOf(RateLimitExceededException.class);
        for (int index = 0; index < 90; index++) {
            limits.checkRequest(request, false);
        }
        assertThatThrownBy(() -> limits.checkRequest(request, false)).isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> limits.checkRequest(request("203.0.113.11"), true)).doesNotThrowAnyException();
        now.set(Duration.ofMinutes(1).toNanos());
        assertThatCode(() -> limits.checkRequest(request, true)).doesNotThrowAnyException();
    }

    private MockHttpServletRequest request(String remoteAddr) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
