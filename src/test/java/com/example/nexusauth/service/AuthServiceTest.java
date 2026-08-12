package com.example.nexusauth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.nexusauth.dto.address.AddressRequest;
import com.example.nexusauth.dto.password.PasswordLoginRequest;
import com.example.nexusauth.dto.registration.PasswordRegistrationStartRequest;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {
    private ProfileRepository profiles;
    private AuthMethodRepository methods;
    private SessionService sessions;
    private PasswordEncoder encoder;
    private FirebaseIdentityService firebase;
    private AuthService service;

    @BeforeEach
    void setUp() {
        profiles = mock(ProfileRepository.class);
        methods = mock(AuthMethodRepository.class);
        sessions = mock(SessionService.class);
        encoder = mock(PasswordEncoder.class);
        firebase = mock(FirebaseIdentityService.class);
        service = new AuthService(profiles, methods, mock(PendingFlowService.class),
                firebase, sessions, encoder, mock(RefreshTokenService.class),
                mock(RegistrationPersistenceService.class), mock(CompanyRepository.class), mock(PlanRepository.class));
    }

    @Test
    void rejectsAdminFromPublicRegistration() {
        var request = new PasswordRegistrationStartRequest(ProfileType.ADMIN, "admin@example.com",
                "password123", "Admin", java.util.List.of("+5511999999999"),
                new AddressRequest("Centro", "Rua A", "1", "01001000", "São Paulo", "SP"),
                null, null, null);

        assertThatThrownBy(() -> service.startPasswordRegistration(request))
                .isInstanceOf(AuthService.InvalidRegistrationException.class);
    }

    @Test
    void rejectsHouseholdOnPlatformChannel() {
        Profile profile = new Profile(1, "user@example.com", "User", ProfileType.HOUSEHOLD, ProfileStatus.ACTIVE);
        when(profiles.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(profile));
        when(methods.findByProfileIdAndProvider(1, AuthProvider.PASSWORD)).thenReturn(Optional.of(
                new AuthMethod(1, AuthProvider.PASSWORD, "hash")));
        when(encoder.matches("password123", "hash")).thenReturn(true);

        var request = new PasswordLoginRequest("user@example.com", "password123", Channel.PLATFORM);
        assertThatThrownBy(() -> service.passwordLogin(request))
                .isInstanceOf(AuthService.ChannelForbiddenException.class);
    }

    @Test
    void rejectsWrongPassword() {
        Profile profile = new Profile(1, "user@example.com", "User", ProfileType.HOUSEHOLD, ProfileStatus.ACTIVE);
        when(profiles.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(profile));
        when(methods.findByProfileIdAndProvider(1, AuthProvider.PASSWORD)).thenReturn(Optional.of(
                new AuthMethod(1, AuthProvider.PASSWORD, "hash")));
        when(encoder.matches("wrong-password", "hash")).thenReturn(false);

        var request = new PasswordLoginRequest("user@example.com", "wrong-password", Channel.MOBILE);
        assertThatThrownBy(() -> service.passwordLogin(request))
                .isInstanceOf(AuthService.InvalidCredentialsException.class);
    }

    @Test
    void rejectsBlockedProfile() {
        Profile profile = new Profile(1, "user@example.com", "User", ProfileType.HOUSEHOLD, ProfileStatus.BLOCKED);
        when(profiles.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(profile));
        when(methods.findByProfileIdAndProvider(1, AuthProvider.PASSWORD)).thenReturn(Optional.of(
                new AuthMethod(1, AuthProvider.PASSWORD, "hash")));
        when(encoder.matches("password123", "hash")).thenReturn(true);

        var request = new PasswordLoginRequest("user@example.com", "password123", Channel.MOBILE);
        assertThatThrownBy(() -> service.passwordLogin(request))
                .isInstanceOf(AuthService.ProfileUnavailableException.class);
    }

    @Test
    void rejectsProviderLinkForBlockedProfile() {
        Profile profile = new Profile(1, "user@example.com", "User", ProfileType.HOUSEHOLD, ProfileStatus.BLOCKED);
        when(firebase.verify("firebase-token")).thenReturn(new FirebaseIdentityService.Identity(
                "firebase-uid", "user@example.com", "User", null, AuthProvider.GOOGLE));
        when(profiles.findById(1)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.linkFirebase(1, "firebase-token"))
                .isInstanceOf(AuthService.ProfileUnavailableException.class);
    }
}
