package com.securedoc.securedoc_ai.dto;

import java.util.List;

public record RelevancePreviewResponse(
        List<String> queryTerms,
        List<RelevanceChunkResponse> chunks
) {
}
