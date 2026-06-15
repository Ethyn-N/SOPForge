package com.securedoc.securedoc_ai.repository;

import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByOwner(User owner);

    Optional<Document> findByIdAndOwner(Long id, User owner);
}