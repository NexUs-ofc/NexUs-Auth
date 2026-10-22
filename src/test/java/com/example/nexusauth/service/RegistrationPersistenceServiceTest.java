package com.example.nexusauth.service;

import com.example.nexusauth.dto.registration.RegistrationData;
import com.example.nexusauth.geo.GeoPoint;
import com.example.nexusauth.geo.GeocodingService;
import com.example.nexusauth.geo.GeocodingUnavailableException;
import com.example.nexusauth.model.Address;
import com.example.nexusauth.model.AddressData;
import com.example.nexusauth.model.AuthMethod;
import com.example.nexusauth.model.AuthProvider;
import com.example.nexusauth.model.Company;
import com.example.nexusauth.model.Plan;
import com.example.nexusauth.model.Profile;
import com.example.nexusauth.model.ProfileType;
import com.example.nexusauth.repository.AddressRepository;
import com.example.nexusauth.repository.AuthMethodRepository;
import com.example.nexusauth.repository.CompanyRepository;
import com.example.nexusauth.repository.PlanRepository;
import com.example.nexusauth.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RegistrationPersistenceServiceTest {
    private static final AddressData ADDRESS = new AddressData(
            "Bela Vista", "Avenida Paulista", "1578", "01310200", "Sao Paulo", "SP");
    private static final GeoPoint POINT = new GeoPoint(
            new BigDecimal("-23.5614961"), new BigDecimal("-46.6559677"));

    private AddressRepository addresses;
    private ProfileRepository profiles;
    private AuthMethodRepository methods;
    private CompanyRepository companies;
    private PlanRepository plans;
    private GeocodingService geocoding;
    private RegistrationPersistenceService service;

    @BeforeEach
    void setUp() {
        addresses = mock(AddressRepository.class);
        profiles = mock(ProfileRepository.class);
        methods = mock(AuthMethodRepository.class);
        companies = mock(CompanyRepository.class);
        plans = mock(PlanRepository.class);
        geocoding = mock(GeocodingService.class);
        service = new RegistrationPersistenceService(addresses, profiles, methods, companies, plans, geocoding);
        when(addresses.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(profiles.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @ParameterizedTest
    @EnumSource(AuthProvider.class)
    void persistsHouseholdCoordinatesForEachRegistrationProvider(AuthProvider provider) {
        assertCoordinatesPersisted(ProfileType.HOUSEHOLD, provider);
        verifyNoInteractions(companies, plans);
    }

    @ParameterizedTest
    @EnumSource(AuthProvider.class)
    void persistsCompanyCoordinatesWhenAddressIsProvided(AuthProvider provider) {
        assertCoordinatesPersisted(ProfileType.COMPANY, provider);
        verify(companies).save(any(Company.class));
    }

    @ParameterizedTest
    @EnumSource(AuthProvider.class)
    void allowsCompanyWithoutAddressAndDoesNotCallGeocoding(AuthProvider provider) {
        Profile profile = service.create(registration(ProfileType.COMPANY, provider, null));

        assertThat(ReflectionTestUtils.getField(profile, "address")).isNull();
        verifyNoInteractions(geocoding, addresses);
        verify(companies).save(any(Company.class));
        verify(methods).save(any(AuthMethod.class));
    }

    @Test
    void rejectsHouseholdWithoutAddressBeforeWritingAnything() {
        assertThatThrownBy(() -> service.create(registration(ProfileType.HOUSEHOLD, AuthProvider.PASSWORD, null)))
                .isInstanceOf(AuthService.InvalidRegistrationException.class);
        verifyNoInteractions(geocoding, addresses, profiles, methods, companies);
    }

    @Test
    void rejectsUnmatchedAddressBeforeWritingAnything() {
        when(geocoding.geocode(ADDRESS)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(registration(ProfileType.HOUSEHOLD, AuthProvider.PASSWORD, ADDRESS)))
                .isInstanceOf(AuthService.InvalidRegistrationException.class)
                .hasMessageContaining("localizar o endereco");
        verifyNoInteractions(addresses, profiles, methods, companies);
    }

    @Test
    void doesNotWriteAnythingWhenProviderIsUnavailable() {
        when(geocoding.geocode(ADDRESS)).thenThrow(new GeocodingUnavailableException(new RuntimeException()));

        assertThatThrownBy(() -> service.create(registration(ProfileType.HOUSEHOLD, AuthProvider.GOOGLE, ADDRESS)))
                .isInstanceOf(GeocodingUnavailableException.class);
        verifyNoInteractions(addresses, profiles, methods, companies);
    }

    private void assertCoordinatesPersisted(ProfileType type, AuthProvider provider) {
        when(geocoding.geocode(ADDRESS)).thenReturn(Optional.of(POINT));
        if (type == ProfileType.COMPANY) {
            when(plans.getReferenceById(1)).thenReturn(mock(Plan.class));
        }

        Profile profile = service.create(registration(type, provider, ADDRESS));

        ArgumentCaptor<Address> addressCaptor = ArgumentCaptor.forClass(Address.class);
        verify(addresses).save(addressCaptor.capture());
        Address saved = addressCaptor.getValue();
        assertThat(saved.getLatitude()).isEqualByComparingTo(POINT.latitude());
        assertThat(saved.getLongitude()).isEqualByComparingTo(POINT.longitude());
        assertThat(ReflectionTestUtils.getField(profile, "address")).isSameAs(saved);
        ArgumentCaptor<AuthMethod> methodCaptor = ArgumentCaptor.forClass(AuthMethod.class);
        verify(methods).save(methodCaptor.capture());
        assertThat(methodCaptor.getValue().provider()).isEqualTo(provider);
        verify(geocoding).geocode(ADDRESS);
    }

    private RegistrationData registration(ProfileType type, AuthProvider provider, AddressData address) {
        return new RegistrationData(type, "user@example.com", "User", List.of(), address, null,
                type == ProfileType.COMPANY ? "12345678000100" : null,
                type == ProfileType.COMPANY ? 1L : null, provider, "credential");
    }
}
