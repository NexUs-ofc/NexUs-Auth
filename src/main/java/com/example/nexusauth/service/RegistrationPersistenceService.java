package com.example.nexusauth.service;

import com.example.nexusauth.dto.registration.RegistrationData;
import com.example.nexusauth.model.Address;
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

    public RegistrationPersistenceService(AddressRepository addresses, ProfileRepository profiles,
                                          AuthMethodRepository authMethods, CompanyRepository companies,
                                          PlanRepository plans) {
        this.addresses = addresses;
        this.profiles = profiles;
        this.authMethods = authMethods;
        this.companies = companies;
        this.plans = plans;
    }

    @Transactional
    public Profile create(RegistrationData data) {
        Address address = data.address() == null
        ? null
        : addresses.save(new Address(data.address()));
        Profile profile = profiles.save(new Profile(
                address,
                data.email(),
                data.name(),
                data.type(),
                data.profileImageUrl(),
                data.phones()
        ));
        authMethods.save(new AuthMethod(profile, data.provider(), data.credential()));

        if (data.type() == ProfileType.COMPANY) {
            companies.save(new Company(plans.getReferenceById(Math.toIntExact(data.planId())), data.cnpj(), profile));
        }
        return profile;
    }
}
