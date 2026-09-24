package org.hrcopilot.retrieval;

import java.util.UUID;

public record RetrievedChunk(
    UUID chunkId,
    UUID sourceDocumentId,
    String sourceFileName,
    String sectionPage,
    String content,
    double rrfScore,
    double denseDistance
) {}
