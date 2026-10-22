package com.example.nexusauth.service;

import com.example.nexusauth.dto.registration.RegistrationData;
import com.example.nexusauth.geo.GeoPoint;
import com.example.nexusauth.geo.GeocodingService;
import com.example.nexusauth.model.Address;
import com.example.nexusauth.model.AddressData;
import com.example.nexusauth.model.AuthMethod;
import com.example.nexusauth.model.Company;
import com.example.nexusauth.model.Profile;
import com.example.nexusauth.model.ProfileType;
import com.example.nexusauth.repository.AddressRepository;
import com.example.nexusauth.repository.AuthMethodRepository;
import com.example.nexusauth.repository.CompanyRepository;
import com.example.nexusauth.repository.PlanRepository;
import com.example.nexusauth.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationPersistenceService {
    private final AddressRepository addresses;
    private final ProfileRepository profiles;
    private final AuthMethodRepository authMethods;
    private final CompanyRepository companies;
    private final PlanRepository plans;
    private final GeocodingService geocoding;

    public RegistrationPersistenceService(AddressRepository addresses, ProfileRepository profiles,
                                          AuthMethodRepository authMethods, CompanyRepository companies,
                                          PlanRepository plans, GeocodingService geocoding) {
        this.addresses = addresses;
        this.profiles = profiles;
        this.authMethods = authMethods;
        this.companies = companies;
        this.plans = plans;
        this.geocoding = geocoding;
    }

    @Transactional
    public Profile create(RegistrationData data) {
        if (data.address() == null && data.type() != ProfileType.COMPANY) {
            throw new AuthService.InvalidRegistrationException("Endereco e obrigatorio para este tipo de perfil");
        }
        Address address = data.address() == null ? null : saveAddress(data.address());
        Profile profile = profiles.save(new Profile(address, data.email(), data.name(), data.type(),
                data.profileImageUrl(), data.phones()));
        authMethods.save(new AuthMethod(profile, data.provider(), data.credential()));
        if (data.type() == ProfileType.COMPANY) {
            companies.save(new Company(plans.getReferenceById(Math.toIntExact(data.planId())), data.cnpj(), profile));
        }
        return profile;
    }

    private Address saveAddress(AddressData data) {
        GeoPoint point = geocoding.geocode(data)
                .orElseThrow(() -> new AuthService.InvalidRegistrationException(
                        "Nao foi possivel localizar o endereco informado. Confira os dados do endereco."));
        return addresses.save(new Address(data, point.latitude(), point.longitude()));
    }
}
