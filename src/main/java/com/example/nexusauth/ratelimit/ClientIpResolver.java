package com.example.nexusauth.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import java.util.Arrays;
import java.util.List;

public final class ClientIpResolver {

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(String trustedProxies) {
        this.trustedProxies = Arrays.stream((trustedProxies == null ? "" : trustedProxies).split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).map(IpAddressMatcher::new).toList();
    }

    public String resolve(HttpServletRequest request) {
        HttpServletRequest original = request;
        while (original instanceof HttpServletRequestWrapper wrapper
                && wrapper.getRequest() instanceof HttpServletRequest wrapped) {
            original = wrapped;
        }
        String peer = original.getRemoteAddr();
        String forwarded = original.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.length() > 2048) {
            return peer;
        }
        String[] hops = forwarded.split(",", -1);
        for (int index = hops.length - 1; index >= 0 && isTrusted(peer); index--) {
            String candidate = hops[index].trim();
            if (!candidate.matches("[0-9a-fA-F:.]+")) {
                return original.getRemoteAddr();
            }
            try {
                new IpAddressMatcher(candidate);
            } catch (IllegalArgumentException exception) {
                return original.getRemoteAddr();
            }
            peer = candidate;
        }
        return peer;
    }

    private boolean isTrusted(String address) {
        return trustedProxies.stream().anyMatch(proxy -> proxy.matches(address));
    }
}
