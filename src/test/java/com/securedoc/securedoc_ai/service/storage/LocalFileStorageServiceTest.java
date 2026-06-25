package com.securedoc.securedoc_ai.service.storage;

import com.securedoc.securedoc_ai.config.StorageProperties;
import com.securedoc.securedoc_ai.exception.BadRequestException;
import com.securedoc.securedoc_ai.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDirectory;

    private LocalFileStorageService storageService;

    @BeforeEach
    void setUp() {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setUploadDir(tempDirectory.resolve("documents").toString());
        storageService = new LocalFileStorageService(storageProperties);
    }

    @Test
    void storeAndLoadRoundTrip() {
        byte[] content = "document contents".getBytes();

        storageService.store("stored.txt", new ByteArrayInputStream(content));

        assertTrue(Files.exists(tempDirectory.resolve("documents/stored.txt")));
        assertArrayEquals(content, storageService.load("stored.txt"));
    }

    @Test
    void deleteRemovesStoredFile() {
        storageService.store("stored.txt", new ByteArrayInputStream("content".getBytes()));

        storageService.delete("stored.txt");

        assertFalse(Files.exists(tempDirectory.resolve("documents/stored.txt")));
        assertThrows(NotFoundException.class, () -> storageService.load("stored.txt"));
    }

    @Test
    void rejectsFileNamesOutsideUploadDirectory() {
        assertThrows(
                BadRequestException.class,
                () -> storageService.store("../outside.txt", new ByteArrayInputStream("content".getBytes()))
        );
    }
}
