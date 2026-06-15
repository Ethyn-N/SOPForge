package com.securedoc.securedoc_ai.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Setter
    private String originalFileName;
    @Setter
    private String storedFileName;
    @Setter
    private String fileType;
    @Setter
    private Long fileSize;
    @Setter
    private String storageUrl;
    @Setter
    private LocalDateTime uploadedAt;

    public Document(String originalFileName, String storedFileName, String fileType, Long fileSize, String storageUrl) {
        this.originalFileName = originalFileName;
        this.storedFileName = storedFileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.storageUrl = storageUrl;
        this.uploadedAt = LocalDateTime.now();
    }

}
