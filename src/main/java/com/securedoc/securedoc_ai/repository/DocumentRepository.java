package com.securedoc.securedoc_ai.repository;

import com.securedoc.securedoc_ai.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
}