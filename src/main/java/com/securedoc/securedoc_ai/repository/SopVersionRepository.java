package com.securedoc.securedoc_ai.repository;

import com.securedoc.securedoc_ai.model.Sop;
import com.securedoc.securedoc_ai.model.SopVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SopVersionRepository extends JpaRepository<SopVersion, Long> {

    List<SopVersion> findBySopOrderByVersionNumberAsc(Sop sop);

    Optional<SopVersion> findByIdAndSop(Long id, Sop sop);

    int countBySop(Sop sop);
}
