package com.example.nexusauth.geo;

import com.example.nexusauth.model.AddressData;
import java.util.Optional;

public interface GeocodingService {
    Optional<GeoPoint> geocode(AddressData address);
}
