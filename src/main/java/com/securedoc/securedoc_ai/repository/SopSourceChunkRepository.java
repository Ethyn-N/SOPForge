package com.securedoc.securedoc_ai.repository;

import com.securedoc.securedoc_ai.model.Sop;
import com.securedoc.securedoc_ai.model.SopSourceChunk;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SopSourceChunkRepository extends JpaRepository<SopSourceChunk, Long> {

    @EntityGraph(attributePaths = {"documentChunk", "documentChunk.document"})
    List<SopSourceChunk> findBySopOrderByDocumentChunkDocumentIdAscDocumentChunkChunkIndexAsc(Sop sop);
}
