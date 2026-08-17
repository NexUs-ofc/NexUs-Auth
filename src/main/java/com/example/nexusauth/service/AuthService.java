package com.example.nexusauth.service;

import com.example.nexusauth.dto.address.AddressRequest;
import com.example.nexusauth.dto.auth.FirebaseAuthenticateRequest;
import com.example.nexusauth.dto.auth.FirebaseAuthenticateResponse;
import com.example.nexusauth.dto.registration.FirebaseRegistrationRequiredResponse;
import com.example.nexusauth.dto.registration.FirebaseRegistrationStartRequest;
import com.example.nexusauth.dto.registration.PasswordRegistrationStartRequest;
import com.example.nexusauth.dto.registration.RegistrationData;
import com.example.nexusauth.dto.password.PasswordLoginRequest;
import com.example.nexusauth.dto.password.ResetPasswordRequest;
import com.example.nexusauth.dto.session.SessionResponse;
import com.example.nexusauth.model.AddressData;
import com.example.nexusauth.model.AuthMethod;
import com.example.nexusauth.model.AuthProvider;
import com.example.nexusauth.model.Channel;
import com.example.nexusauth.model.Profile;
import com.example.nexusauth.model.ProfileStatus;
import com.example.nexusauth.model.ProfileType;
import com.example.nexusauth.repository.AuthMethodRepository;
import com.example.nexusauth.repository.CompanyRepository;
import com.example.nexusauth.repository.PlanRepository;
import com.example.nexusauth.repository.ProfileRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final ProfileRepository profiles;
    private final AuthMethodRepository authMethods;
    private final PendingFlowService pendingFlows;
    private final FirebaseIdentityService firebase;
    private final SessionService sessions;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final RegistrationPersistenceService registrations;
    private final CompanyRepository companies;
    private final PlanRepository plans;

    public AuthService(ProfileRepository profiles, AuthMethodRepository authMethods,
                       PendingFlowService pendingFlows, FirebaseIdentityService firebase,
                       SessionService sessions, PasswordEncoder passwordEncoder,
                       RefreshTokenService refreshTokens, RegistrationPersistenceService registrations,
                       CompanyRepository companies, PlanRepository plans) {
        this.profiles = profiles;
        this.authMethods = authMethods;
        this.pendingFlows = pendingFlows;
        this.firebase = firebase;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.registrations = registrations;
        this.companies = companies;
        this.plans = plans;
    }

    public String startPasswordRegistration(PasswordRegistrationStartRequest request) {
        logger.info("Iniciando cadastro por senha para email={} tipo={}", request.email(), request.type());

        validatePublicType(request.type());
        ensureEmailAvailable(request.email());
        validateCompany(request.type(), request.cnpj(), request.planId());

        RegistrationData data = new RegistrationData(
                request.type(),
                normalizeEmail(request.email()),
                request.name(),
                request.phones(),
                address(request.type(), request.address()),
                request.profileImageUrl(),
                request.cnpj(),
                request.planId(),
                AuthProvider.PASSWORD,
                passwordEncoder.encode(request.password())
        );

        String registrationId = pendingFlows.startRegistration(data);

        logger.info("Cadastro por senha iniciado com sucesso para email={}", request.email());

        return registrationId;
    }

    @Transactional
    public SessionService.Session verifyRegistration(String registrationId, String otp) {
        logger.info("Iniciando verificação de cadastro registrationId={}", registrationId);

        RegistrationData data = pendingFlows.verifyRegistration(registrationId, otp);

        logger.debug("Dados do cadastro recuperados com sucesso registrationId={} provider={}", registrationId, data.provider());

        ensureEmailAvailable(data.email());
        validateCompany(data.type(), data.cnpj(), data.planId());

        Profile profile = registrations.create(data);

        logger.info("Perfil criado com sucesso profileId={} registrationId={}", profile.id(), registrationId);

        pendingFlows.completeRegistration(registrationId);

        SessionService.Session session = sessions.issue(profile);

        logger.info("Cadastro concluído e sessão criada com sucesso profileId={}", profile.id());

        return session;
    }

    public SessionService.Session passwordLogin(PasswordLoginRequest request) {
        logger.info("Tentativa de login por senha email={} channel={}", request.email(), request.channel());

        Profile profile = profiles.findByEmailIgnoreCase(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        logger.debug("Perfil encontrado para login profileId={}", profile.id());

        AuthMethod method = authMethods.findByProfileIdAndProvider(profile.id(), AuthProvider.PASSWORD)
                .orElseThrow(InvalidCredentialsException::new);

        logger.debug("Método de autenticação PASSWORD encontrado profileId={}", profile.id());

        if (!passwordEncoder.matches(request.password(), method.credential())) {
            logger.warn("Falha de autenticação por senha profileId={}", profile.id());
            throw new InvalidCredentialsException();
        }

        validateLogin(profile, request.channel());

        SessionService.Session session = sessions.issue(profile);

        logger.info("Login por senha realizado com sucesso profileId={} channel={}", profile.id(), request.channel());

        return session;
    }

    public FirebaseAuthenticateResponse firebaseAuthenticate(FirebaseAuthenticateRequest request) {
        logger.info("Iniciando autenticação através do Firebase");

        FirebaseIdentityService.Identity identity = firebase.verify(request.idToken());

        logger.debug("Identidade Firebase validada provider={} email={}", identity.provider(), identity.email());

        return authMethods.findByProviderAndCredential(identity.provider(), identity.uid())
                .map(method -> {
                    logger.debug("Método de autenticação encontrado para identidade Firebase profileId={} provider={}", method.profileId(), identity.provider());

                    Profile profile = profiles.findById(method.profileId())
                            .orElseThrow(InvalidCredentialsException::new);

                    validateLogin(profile, request.channel());

                    SessionService.Session session = sessions.issue(profile);

                    SessionResponse response = new SessionResponse(
                            session.accessToken(),
                            session.accessTokenExpiresAt(),
                            session.refreshToken(),
                            session.refreshTokenExpiresAt()
                    );

                    logger.info("Login através do Firebase realizado com sucesso profileId={} provider={} channel={}", profile.id(), identity.provider(), request.channel());

                    return new FirebaseAuthenticateResponse(false, response, null);
                })
                .orElseGet(() -> {
                    logger.debug("Identidade Firebase não possui método de autenticação vinculado provider={} email={}", identity.provider(), identity.email());

                    if (profiles.findByEmailIgnoreCase(identity.email()).isPresent()) {
                        logger.warn("Identidade Firebase pertence a um email já cadastrado, mas não está vinculada provider={}", identity.provider());
                        throw new AccountRequiresLinkException();
                    }

                    String ticket = pendingFlows.saveFirebaseTicket(identity);

                    logger.info("Ticket Firebase criado para início de cadastro provider={}", identity.provider());

                    var registration = new FirebaseRegistrationRequiredResponse(
                            ticket,
                            identity.email(),
                            identity.name(),
                            identity.picture(),
                            List.of("type", "phones", "address", "cnpj/company", "planId/company")
                    );

                    return new FirebaseAuthenticateResponse(true, null, registration);
                });
    }

    public String startFirebaseRegistration(FirebaseRegistrationStartRequest request) {
        logger.info("Iniciando cadastro através do Firebase");

        FirebaseIdentityService.Identity identity = pendingFlows.getFirebaseTicket(request.firebaseTicket());

        logger.debug("Identidade Firebase recuperada do ticket provider={} email={}", identity.provider(), identity.email());

        validatePublicType(request.type());
        ensureEmailAvailable(identity.email());
        validateCompany(request.type(), request.cnpj(), request.planId());

        String name = request.name() == null || request.name().isBlank() ? identity.name() : request.name();

        if (name == null || name.isBlank())
            throw new InvalidRegistrationException("Nome é obrigatório");

        RegistrationData data = new RegistrationData(
                request.type(),
                normalizeEmail(identity.email()),
                name,
                request.phones(),
                address(request.type(), request.address()),
                identity.picture(),
                request.cnpj(),
                request.planId(),
                identity.provider(),
                identity.uid()
        );

        String registrationId = pendingFlows.startRegistration(data);

        pendingFlows.deleteFirebaseTicket(request.firebaseTicket());

        logger.info("Cadastro Firebase iniciado com sucesso email={} provider={}", identity.email(), identity.provider());

        return registrationId;
    }

    public String startPasswordReset(String email) {
        logger.info("Solicitação de recuperação de senha recebida");

        return profiles.findByEmailIgnoreCase(email)
                .filter(profile -> authMethods.findByProfileIdAndProvider(profile.id(), AuthProvider.PASSWORD).isPresent())
                .map(profile -> {
                    logger.info("Iniciando recuperação de senha para profileId={}", profile.id());
                    return pendingFlows.startPasswordReset(profile.id(), profile.email());
                })
                .orElseGet(() -> {
                    logger.debug("Nenhum perfil elegível encontrado para recuperação de senha");
                    return UUID.randomUUID().toString();
                });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        logger.info("Iniciando redefinição de senha");

        long profileId = pendingFlows.verifyPasswordReset(request.resetId(), request.otp());

        logger.debug("Processo de recuperação de senha validado profileId={}", profileId);

        AuthMethod password = authMethods.findByProfileIdAndProvider(Math.toIntExact(profileId), AuthProvider.PASSWORD)
                .orElseThrow(() -> new IllegalStateException("Perfil não possui autenticação por senha"));

        password.updateCredential(passwordEncoder.encode(request.newPassword()));

        refreshTokens.revokeAll(profileId);

        logger.info("Senha redefinida com sucesso e sessões revogadas profileId={}", profileId);
    }

    @Transactional
    public void linkFirebase(long authenticatedProfileId, String idToken) {
        logger.info("Iniciando vinculação de identidade Firebase profileId={}", authenticatedProfileId);

        FirebaseIdentityService.Identity identity = firebase.verify(idToken);

        logger.debug("Identidade Firebase validada para vinculação provider={} email={}", identity.provider(), identity.email());

        Profile profile = profiles.findById(Math.toIntExact(authenticatedProfileId))
                .orElseThrow(InvalidCredentialsException::new);

        if (profile.status() != ProfileStatus.ACTIVE)
            throw new ProfileUnavailableException();

        if (!profile.email().equalsIgnoreCase(identity.email()))
            throw new EmailMismatchException();

        if (authMethods.findByProviderAndCredential(identity.provider(), identity.uid()).isPresent())
            throw new IdentityAlreadyLinkedException();

        if (identity.provider() == AuthProvider.PASSWORD)
            throw new InvalidRegistrationException("Provider deve ser externo");

        authMethods.save(new AuthMethod(profile, identity.provider(), identity.uid()));

        logger.info("Identidade Firebase vinculada com sucesso profileId={} provider={}", authenticatedProfileId, identity.provider());
    }

    private void validateLogin(Profile profile, Channel channel) {
        logger.debug("Validando acesso de login profileId={} channel={}", profile.id(), channel);

        if (profile.status() != ProfileStatus.ACTIVE)
            throw new ProfileUnavailableException();

        boolean allowed = channel == Channel.PLATFORM
                ? profile.type() == ProfileType.COMPANY || profile.type() == ProfileType.ADMIN
                : profile.type() == ProfileType.COMPANY || profile.type() == ProfileType.HOUSEHOLD;

        if (!allowed)
            throw new ChannelForbiddenException();
    }

    private void validatePublicType(ProfileType type) {
        logger.debug("Validando tipo de perfil para cadastro type={}", type);

        if (type != ProfileType.HOUSEHOLD && type != ProfileType.COMPANY)
            throw new InvalidRegistrationException("Apenas HOUSEHOLD e COMPANY aceitam registro público");
    }

    private void ensureEmailAvailable(String email) {
        logger.debug("Verificando disponibilidade do email");

        if (profiles.findByEmailIgnoreCase(email).isPresent())
            throw new EmailAlreadyUsedException();
    }

    private void validateCompany(ProfileType type, String cnpj, Long planId) {
        logger.debug("Validando dados de empresa type={} possuiCnpj={} possuiPlanId={}", type, cnpj != null, planId != null);

        if (type == ProfileType.COMPANY) {
            if (cnpj == null || planId == null)
                throw new InvalidRegistrationException("CNPJ e plano são obrigatórios");

            if (companies.existsByCnpj(cnpj))
                throw new CnpjAlreadyUsedException();

            if (!plans.existsByIdAndActiveTrue(Math.toIntExact(planId)))
                throw new InvalidRegistrationException("Plano inválido ou inativo");

        } else if (cnpj != null || planId != null) {
            throw new InvalidRegistrationException("CNPJ e plano só se aplicam a COMPANY");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private AddressData address(ProfileType type, AddressRequest value) {
        if (value == null) {
            if (type == ProfileType.HOUSEHOLD)
                throw new InvalidRegistrationException("Endereço é obrigatório para HOUSEHOLD");

            return null;
        }

        return new AddressData(
                value.neighborhood(),
                value.street(),
                value.number(),
                value.cep(),
                value.city(),
                value.state()
        );
    }

    public static class InvalidCredentialsException extends RuntimeException {}

    public static class EmailAlreadyUsedException extends RuntimeException {}

    public static class CnpjAlreadyUsedException extends RuntimeException {}

    public static class AccountRequiresLinkException extends RuntimeException {}

    public static class ProfileUnavailableException extends RuntimeException {}

    public static class ChannelForbiddenException extends RuntimeException {}

    public static class EmailMismatchException extends RuntimeException {}

    public static class IdentityAlreadyLinkedException extends RuntimeException {}

    public static class InvalidRegistrationException extends RuntimeException {
        public InvalidRegistrationException(String message) {
            super(message);
        }
    }
}