package com.example.nexusauth.service;

import com.example.nexusauth.model.Address;
import com.example.nexusauth.model.AuthMethod;
import com.example.nexusauth.model.Company;
import com.example.nexusauth.model.Profile;
import com.example.nexusauth.model.ProfileType;
import com.example.nexusauth.model.RegistrationData;
import com.example.nexusauth.repository.AddressRepository;
import com.example.nexusauth.repository.AuthMethodRepository;
import com.example.nexusauth.repository.CompanyRepository;
import com.example.nexusauth.repository.PlanRepository;
import com.example.nexusauth.repository.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationPersistenceService {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    RegistrationPersistenceService.class
            );

    private final AddressRepository addresses;
    private final ProfileRepository profiles;
    private final AuthMethodRepository authMethods;
    private final CompanyRepository companies;
    private final PlanRepository plans;

    public RegistrationPersistenceService(
            AddressRepository addresses,
            ProfileRepository profiles,
            AuthMethodRepository authMethods,
            CompanyRepository companies,
            PlanRepository plans
    ) {
        this.addresses = addresses;
        this.profiles = profiles;
        this.authMethods = authMethods;
        this.companies = companies;
        this.plans = plans;
    }

    @Transactional
    public Profile create(
            RegistrationData data
    ) {

        logger.info(
                "Iniciando persistência de novo cadastro email={} type={} provider={}",
                data.email(),
                data.type(),
                data.provider()
        );

        logger.debug(
                "Persistindo endereço do novo perfil email={}",
                data.email()
        );

        Address address =
                addresses.save(
                        new Address(
                                data.address()
                        )
                );

        logger.debug(
                "Endereço persistido com sucesso email={}",
                data.email()
        );

        Profile profile =
                profiles.save(
                        new Profile(
                                address,
                                data.email(),
                                data.name(),
                                data.type(),
                                data.profileImageUrl(),
                                data.phones()
                        )
                );

        logger.info(
                "Perfil persistido com sucesso profileId={} email={} type={}",
                profile.id(),
                profile.email(),
                profile.type()
        );

        logger.debug(
                "Persistindo método de autenticação profileId={} provider={}",
                profile.id(),
                data.provider()
        );

        authMethods.save(
                new AuthMethod(
                        profile,
                        data.provider(),
                        data.credential()
                )
        );

        logger.info(
                "Método de autenticação persistido com sucesso profileId={} provider={}",
                profile.id(),
                data.provider()
        );

        if (data.type() == ProfileType.COMPANY) {

            logger.debug(
                    "Cadastro identificado como COMPANY; persistindo dados da empresa profileId={} planId={} cnpjPresente={}",
                    profile.id(),
                    data.planId(),
                    data.cnpj() != null
            );

            Company company =
                    companies.save(
                            new Company(
                                    plans.getReferenceById(
                                            Math.toIntExact(
                                                    data.planId()
                                            )
                                    ),
                                    data.cnpj(),
                                    profile
                            )
                    );

            logger.info(
                    "Empresa persistida com sucesso profileId={} planId={}",
                    profile.id(),
                    data.planId()
            );

        } else {

            logger.debug(
                    "Cadastro não é COMPANY; persistência de empresa ignorada profileId={} type={}",
                    profile.id(),
                    data.type()
            );
        }

        logger.info(
                "Persistência do cadastro concluída com sucesso profileId={} email={} type={}",
                profile.id(),
                profile.email(),
                profile.type()
        );

        return profile;
    }
}