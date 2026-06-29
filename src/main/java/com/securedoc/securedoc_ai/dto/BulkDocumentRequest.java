package com.securedoc.securedoc_ai.dto;

import java.util.List;

public record BulkDocumentRequest(List<Long> documentIds) {
}
