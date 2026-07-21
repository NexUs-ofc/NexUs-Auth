package com.example.nexusauth.repository;

import com.example.nexusauth.model.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Integer> {
    boolean existsByIdAndActiveTrue(int id);
}
