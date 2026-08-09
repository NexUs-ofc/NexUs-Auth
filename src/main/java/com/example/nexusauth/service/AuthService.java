package com.example.nexusauth.service;

import com.example.nexusauth.dto.address.AddressRequest;
import com.example.nexusauth.dto.auth.FirebaseAuthenticateRequest;
import com.example.nexusauth.dto.auth.FirebaseAuthenticateResponse;
import com.example.nexusauth.dto.registration.FirebaseRegistrationRequiredResponse;
import com.example.nexusauth.dto.registration.FirebaseRegistrationStartRequest;
import com.example.nexusauth.dto.registration.PasswordRegistrationStartRequest;
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
import com.example.nexusauth.model.RegistrationData;
import com.example.nexusauth.repository.AuthMethodRepository;
import com.example.nexusauth.repository.CompanyRepository;
import com.example.nexusauth.repository.PlanRepository;
import com.example.nexusauth.repository.ProfileRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
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
        validatePublicType(request.type());
        ensureEmailAvailable(request.email());
        validateCompany(request.type(), request.cnpj(), request.planId());
        RegistrationData data = new RegistrationData(request.type(), normalizeEmail(request.email()), request.name(),
                request.phones(), address(request.address()), request.profileImageUrl(), request.cnpj(), request.planId(),
                AuthProvider.PASSWORD, passwordEncoder.encode(request.password()));
        return pendingFlows.startRegistration(data);
    }

    @Transactional
    public SessionService.Session verifyRegistration(String registrationId, String otp) {
        RegistrationData data = pendingFlows.verifyRegistration(registrationId, otp);
        ensureEmailAvailable(data.email());
        validateCompany(data.type(), data.cnpj(), data.planId());
        Profile profile = registrations.create(data);
        pendingFlows.completeRegistration(registrationId);
        return sessions.issue(profile);
    }

    public SessionService.Session passwordLogin(PasswordLoginRequest request) {
        Profile profile = profiles.findByEmailIgnoreCase(request.email()).orElseThrow(InvalidCredentialsException::new);
        AuthMethod method = authMethods.findByProfileIdAndProvider(profile.id(), AuthProvider.PASSWORD)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), method.credential())) throw new InvalidCredentialsException();
        validateLogin(profile, request.channel());
        return sessions.issue(profile);
    }

    public FirebaseAuthenticateResponse firebaseAuthenticate(FirebaseAuthenticateRequest request) {
        FirebaseIdentityService.Identity identity = firebase.verify(request.idToken());
        return authMethods.findByProviderAndCredential(identity.provider(), identity.uid())
                .map(method -> {
                    Profile profile = profiles.findById(method.profileId()).orElseThrow(InvalidCredentialsException::new);
                    validateLogin(profile, request.channel());
                    SessionService.Session session = sessions.issue(profile);
                    SessionResponse response = new SessionResponse(session.accessToken(), session.accessTokenExpiresAt(),
                            session.refreshToken(), session.refreshTokenExpiresAt());
                    return new FirebaseAuthenticateResponse(false, response, null);
                })
                .orElseGet(() -> {
                    if (profiles.findByEmailIgnoreCase(identity.email()).isPresent()) throw new AccountRequiresLinkException();
                    String ticket = pendingFlows.saveFirebaseTicket(identity);
                    var registration = new FirebaseRegistrationRequiredResponse(ticket, identity.email(),
                            identity.name(), identity.picture(), List.of("type", "phones", "address", "cnpj/company", "planId/company"));
                    return new FirebaseAuthenticateResponse(true, null, registration);
                });
    }

    public String startFirebaseRegistration(FirebaseRegistrationStartRequest request) {
        FirebaseIdentityService.Identity identity = pendingFlows.getFirebaseTicket(request.firebaseTicket());
        validatePublicType(request.type());
        ensureEmailAvailable(identity.email());
        validateCompany(request.type(), request.cnpj(), request.planId());
        String name = request.name() == null || request.name().isBlank() ? identity.name() : request.name();
        if (name == null || name.isBlank()) throw new InvalidRegistrationException("Nome é obrigatório");
        RegistrationData data = new RegistrationData(request.type(), normalizeEmail(identity.email()), name,
                request.phones(), address(request.address()), identity.picture(), request.cnpj(), request.planId(),
                identity.provider(), identity.uid());
        String registrationId = pendingFlows.startRegistration(data);
        pendingFlows.deleteFirebaseTicket(request.firebaseTicket());
        return registrationId;
    }

    public String startPasswordReset(String email) {
        return profiles.findByEmailIgnoreCase(email)
                .filter(profile -> authMethods.findByProfileIdAndProvider(profile.id(), AuthProvider.PASSWORD).isPresent())
                .map(profile -> pendingFlows.startPasswordReset(profile.id(), profile.email()))
                .orElseGet(() -> UUID.randomUUID().toString());
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        long profileId = pendingFlows.verifyPasswordReset(request.resetId(), request.otp());
        AuthMethod password = authMethods.findByProfileIdAndProvider(Math.toIntExact(profileId), AuthProvider.PASSWORD)
                .orElseThrow(() -> new IllegalStateException("Perfil não possui autenticação por senha"));
        password.updateCredential(passwordEncoder.encode(request.newPassword()));
        refreshTokens.revokeAll(profileId);
    }

    @Transactional
    public void linkFirebase(long authenticatedProfileId, String idToken) {
        FirebaseIdentityService.Identity identity = firebase.verify(idToken);
        Profile profile = profiles.findById(Math.toIntExact(authenticatedProfileId))
                .orElseThrow(InvalidCredentialsException::new);
        if (profile.status() != ProfileStatus.ACTIVE) throw new ProfileUnavailableException();
        if (!profile.email().equalsIgnoreCase(identity.email())) throw new EmailMismatchException();
        if (authMethods.findByProviderAndCredential(identity.provider(), identity.uid()).isPresent())
            throw new IdentityAlreadyLinkedException();
        if (identity.provider() == AuthProvider.PASSWORD)
            throw new InvalidRegistrationException("Provider deve ser externo");
        authMethods.save(new AuthMethod(profile, identity.provider(), identity.uid()));
    }

    private void validateLogin(Profile profile, Channel channel) {
        if (profile.status() != ProfileStatus.ACTIVE) throw new ProfileUnavailableException();
        boolean allowed = channel == Channel.PLATFORM
                ? profile.type() == ProfileType.COMPANY || profile.type() == ProfileType.ADMIN
                : profile.type() == ProfileType.COMPANY || profile.type() == ProfileType.HOUSEHOLD;
        if (!allowed) throw new ChannelForbiddenException();
    }

    private void validatePublicType(ProfileType type) {
        if (type != ProfileType.HOUSEHOLD && type != ProfileType.COMPANY)
            throw new InvalidRegistrationException("Apenas HOUSEHOLD e COMPANY aceitam registro público");
    }

    private void ensureEmailAvailable(String email) {
        if (profiles.findByEmailIgnoreCase(email).isPresent()) throw new EmailAlreadyUsedException();
    }

    private void validateCompany(ProfileType type, String cnpj, Long planId) {
        if (type == ProfileType.COMPANY) {
            if (cnpj == null || planId == null) throw new InvalidRegistrationException("CNPJ e plano são obrigatórios");
            if (companies.existsByCnpj(cnpj)) throw new CnpjAlreadyUsedException();
            if (!plans.existsByIdAndActiveTrue(Math.toIntExact(planId)))
                throw new InvalidRegistrationException("Plano inválido ou inativo");
        } else if (cnpj != null || planId != null) {
            throw new InvalidRegistrationException("CNPJ e plano só se aplicam a COMPANY");
        }
    }

    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private AddressData address(AddressRequest value) {
        return new AddressData(value.neighborhood(), value.street(), value.number(), value.cep(), value.city(), value.state());
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
        public InvalidRegistrationException(String message) { super(message); }
    }
}
