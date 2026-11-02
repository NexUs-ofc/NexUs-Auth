package com.example.nexusauth.ratelimit;

import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeadersAndWrappedAddressesWithoutTrustedProxies() {
        var request = request("203.0.113.10", "198.51.100.20");
        var wrapped = new HttpServletRequestWrapper(request) {
            @Override
            public String getRemoteAddr() {
                return "198.51.100.99";
            }
        };
        assertThat(new ClientIpResolver("").resolve(wrapped)).isEqualTo("203.0.113.10");
    }

    @Test
    void usesTheFirstUntrustedHopFromTheRight() {
        var resolver = new ClientIpResolver("10.0.0.0/8, 192.168.0.0/16");
        var request = request("10.0.0.10", "198.51.100.99, 203.0.113.20, 192.168.1.2");
        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.20");
    }

    @Test
    void fallsBackToThePeerForMalformedForwardedHeaders() {
        var resolver = new ClientIpResolver("10.0.0.0/8");
        assertThat(resolver.resolve(request("10.0.0.10", "bad.example"))).isEqualTo("10.0.0.10");
        assertThat(resolver.resolve(request("10.0.0.10", "999.999.999.999"))).isEqualTo("10.0.0.10");
        assertThat(resolver.resolve(request("10.0.0.10", "203.0.113.10,"))).isEqualTo("10.0.0.10");
    }

    private MockHttpServletRequest request(String remoteAddr, String forwarded) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        request.addHeader("X-Forwarded-For", forwarded);
        return request;
    }
}
