package com.example.nexusauth.repository;

import com.example.nexusauth.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Integer> {
    boolean existsByCnpj(String cnpj);
}
