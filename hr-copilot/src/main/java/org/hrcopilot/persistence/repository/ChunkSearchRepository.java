package org.hrcopilot.persistence.repository;

import java.util.List;
import java.util.Set;
import org.hrcopilot.retrieval.RetrievedChunk;

public interface ChunkSearchRepository {
    List<RetrievedChunk> hybridSearch(String query, float[] queryEmbedding, Set<String> categories, String sourceFilter);
}
