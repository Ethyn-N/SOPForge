package com.securedoc.securedoc_ai.repository;

import com.securedoc.securedoc_ai.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
}
