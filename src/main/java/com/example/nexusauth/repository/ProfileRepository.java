package com.example.nexusauth.repository;

import com.example.nexusauth.model.Profile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, Integer> {
    Optional<Profile> findByEmailIgnoreCase(String email);
}
