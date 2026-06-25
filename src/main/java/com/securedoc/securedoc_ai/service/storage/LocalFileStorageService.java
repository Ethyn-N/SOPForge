package com.securedoc.securedoc_ai.service.storage;

import com.securedoc.securedoc_ai.config.StorageProperties;
import com.securedoc.securedoc_ai.exception.BadRequestException;
import com.securedoc.securedoc_ai.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "securedoc.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private final StorageProperties storageProperties;

    @Override
    public void store(String storedFileName, InputStream inputStream) {
        Path storedFilePath = resolveStoredFilePath(storedFileName);

        try {
            Files.createDirectories(uploadPath());
            Files.copy(inputStream, storedFilePath);
        } catch (IOException exception) {
            throw new BadRequestException("File upload could not be completed.");
        }
    }

    @Override
    public byte[] load(String storedFileName) {
        Path storedFilePath = resolveStoredFilePath(storedFileName);

        if (!Files.exists(storedFilePath)) {
            throw new NotFoundException("Document file could not be found.");
        }

        try {
            return Files.readAllBytes(storedFilePath);
        } catch (IOException exception) {
            throw new BadRequestException("Document file could not be read.");
        }
    }

    @Override
    public void delete(String storedFileName) {
        try {
            Files.deleteIfExists(resolveStoredFilePath(storedFileName));
        } catch (IOException exception) {
            throw new BadRequestException("Document file could not be deleted.");
        }
    }

    @Override
    public String storageUrl(String storedFileName) {
        return "/uploads/documents/" + storedFileName;
    }

    private Path resolveStoredFilePath(String storedFileName) {
        Path uploadPath = uploadPath();
        Path storedFilePath = uploadPath.resolve(storedFileName).normalize();

        if (!storedFilePath.startsWith(uploadPath)) {
            throw new BadRequestException("Invalid stored file name.");
        }

        return storedFilePath;
    }

    private Path uploadPath() {
        return Path.of(storageProperties.getUploadDir()).toAbsolutePath().normalize();
    }
}
