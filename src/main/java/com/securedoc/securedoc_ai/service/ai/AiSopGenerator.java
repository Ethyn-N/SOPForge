package com.securedoc.securedoc_ai.service.ai;

import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.User;

import java.util.List;

public interface AiSopGenerator {

    default GeneratedSopDraft generate(Document document, User user) {
        return generate(List.of(document), null, null, user);
    }

    GeneratedSopDraft generate(List<Document> documents, String requestedTitle, String instructions, User user);
}
