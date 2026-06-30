package com.securedoc.securedoc_ai.repository;

import com.securedoc.securedoc_ai.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM companies WHERE id = :companyId", nativeQuery = true)
    void deleteCascadeById(@Param("companyId") Long companyId);
}
