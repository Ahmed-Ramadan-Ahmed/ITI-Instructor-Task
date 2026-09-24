package org.hrcopilot.api.dto;

import java.util.List;

/**
 * Response body for POST /api/ask.
 */
public record AskResponse(
    String answer,
    boolean refused,
    List<CitationDto> citations
) {
    public record CitationDto(
        String sourceDocumentId,
        String sourceFileName,
        String sectionOrPage,
        String chunkId
    ) {}
}
