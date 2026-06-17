package com.securedoc.securedoc_ai.service.ai;

import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.User;

public interface AiSopGenerator {

    GeneratedSopDraft generate(Document document, User user);
}
