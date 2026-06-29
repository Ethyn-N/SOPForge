package com.securedoc.securedoc_ai.repository;

import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.Company;
import com.securedoc.securedoc_ai.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByOwner(User owner);

    List<Document> findByCompany(Company company);

    Optional<Document> findByIdAndOwner(Long id, User owner);

    Optional<Document> findByIdAndCompany(Long id, Company company);

    List<Document> findAllByIdInAndCompany(List<Long> ids, Company company);
}
