package com.example.nexusauth.repository;

import com.example.nexusauth.model.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProfileRepository extends JpaRepository<Profile, Integer> {
    Optional<Profile> findByEmailIgnoreCase(String email);
}
