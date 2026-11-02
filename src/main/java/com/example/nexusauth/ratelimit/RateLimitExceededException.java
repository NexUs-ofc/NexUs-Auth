package com.example.nexusauth.ratelimit;

public final class RateLimitExceededException extends RuntimeException {

    private final long retryAfter;

    public RateLimitExceededException(long retryAfter) {
        super("Muitas requisicoes. Tente novamente mais tarde.");
        this.retryAfter = retryAfter;
    }

    public long retryAfter() {
        return retryAfter;
    }
}
