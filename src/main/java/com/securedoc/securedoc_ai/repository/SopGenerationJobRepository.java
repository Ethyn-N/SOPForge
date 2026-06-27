package com.securedoc.securedoc_ai.repository;

import com.securedoc.securedoc_ai.model.Company;
import com.securedoc.securedoc_ai.model.SopGenerationJob;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SopGenerationJobRepository extends JpaRepository<SopGenerationJob, Long> {

    @EntityGraph(attributePaths = {"company", "owner", "sourceDocuments", "resultSop"})
    Optional<SopGenerationJob> findById(Long id);

    @EntityGraph(attributePaths = {"company", "owner", "sourceDocuments", "resultSop"})
    Optional<SopGenerationJob> findByIdAndCompany(Long id, Company company);

    @EntityGraph(attributePaths = {"company", "owner", "sourceDocuments", "resultSop"})
    List<SopGenerationJob> findByCompanyOrderByCreatedAtDesc(Company company);
}
