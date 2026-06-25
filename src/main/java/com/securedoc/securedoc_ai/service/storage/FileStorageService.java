package com.securedoc.securedoc_ai.service.storage;

import java.io.InputStream;

public interface FileStorageService {

    void store(String storedFileName, InputStream inputStream);

    byte[] load(String storedFileName);

    void delete(String storedFileName);

    String storageUrl(String storedFileName);
}
