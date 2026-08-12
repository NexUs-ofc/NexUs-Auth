package com.example.nexusauth.repository;

import com.example.nexusauth.model.AuthMethod;
import com.example.nexusauth.model.AuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthMethodRepository extends JpaRepository<AuthMethod, Integer> {
    Optional<AuthMethod> findByProfileIdAndProvider(int profileId, AuthProvider provider);

    Optional<AuthMethod> findByProviderAndCredential(AuthProvider provider, String credential);
}
