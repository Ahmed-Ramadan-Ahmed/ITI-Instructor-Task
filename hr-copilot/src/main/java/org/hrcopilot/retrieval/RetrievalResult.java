package org.hrcopilot.retrieval;

import java.util.List;

public record RetrievalResult(
    List<RetrievedChunk> chunks,
    boolean shouldRefuse
) {}
