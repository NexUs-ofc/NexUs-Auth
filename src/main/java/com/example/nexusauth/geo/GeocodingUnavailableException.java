package com.example.nexusauth.geo;

public class GeocodingUnavailableException extends RuntimeException {
    public GeocodingUnavailableException() {
        this(null);
    }

    public GeocodingUnavailableException(Throwable cause) {
        super("Servico de localizacao indisponivel. Tente novamente mais tarde.", cause);
    }
}
