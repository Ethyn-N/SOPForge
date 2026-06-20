package com.securedoc.securedoc_ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "sop_source_chunks")
public class SopSourceChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sop_id", nullable = false)
    private Sop sop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_chunk_id", nullable = false)
    private DocumentChunk documentChunk;

    @Column(nullable = false)
    private Integer score;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String matchedTerms;

    public SopSourceChunk(Sop sop, DocumentChunk documentChunk, Integer score, List<String> matchedTerms) {
        this.sop = sop;
        this.documentChunk = documentChunk;
        this.score = score;
        this.matchedTerms = String.join(",", matchedTerms);
    }

    public List<String> getMatchedTermsList() {
        if (matchedTerms == null || matchedTerms.isBlank()) {
            return List.of();
        }

        return Arrays.stream(matchedTerms.split(","))
                .filter(term -> !term.isBlank())
                .toList();
    }
}
