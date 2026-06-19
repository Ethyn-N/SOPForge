package com.securedoc.securedoc_ai.repository;

import com.securedoc.securedoc_ai.model.Sop;
import com.securedoc.securedoc_ai.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SopRepository extends JpaRepository<Sop, Long> {

    @EntityGraph(attributePaths = {"sourceDocuments", "owner"})
    List<Sop> findByOwner(User owner);

    @EntityGraph(attributePaths = {"sourceDocuments", "owner"})
    Optional<Sop> findByIdAndOwner(Long id, User owner);
}
