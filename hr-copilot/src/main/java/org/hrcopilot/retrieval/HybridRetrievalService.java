package org.hrcopilot.retrieval;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.hrcopilot.observability.TokenUsageService;
import org.hrcopilot.persistence.repository.ChunkSearchRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HybridRetrievalService {

    private final ChunkSearchRepository chunkSearch;
    private final EmbeddingModel embeddingModel;
    private final TokenUsageService usage;

    // Default threshold for refusal (measured in cosine distance: lower is better)
    // distance = 1 - cosine_similarity. If we want sim >= 0.65, distance <= 0.35.
    private static final double MAX_DENSE_DISTANCE_THRESHOLD = 0.35;

    /**
     * @param query The user's search query
     * @param agentAllowedCategories The strictly enforced categories this agent is allowed to search
     * @param requestedCategories Optional narrow-down requested by the LLM
     */
    public RetrievalResult search(String query, Set<String> agentAllowedCategories, Set<String> requestedCategories) {
        // Intersect, never widen
        Set<String> effectiveCategories = (requestedCategories == null || requestedCategories.isEmpty()) 
            ? agentAllowedCategories 
            : requestedCategories.stream()
                .filter(agentAllowedCategories::contains)
                .collect(Collectors.toSet());

        if (effectiveCategories.isEmpty()) {
            return new RetrievalResult(Collections.emptyList(), true); // Refuse if scope resolves to empty
        }

        float[] queryEmbedding = embeddingModel.embed(query);

        String source="SCREENING".equals(org.slf4j.MDC.get("source")) ? "SCREENING":"ASK";
        usage.record(source, "EMBEDDING", "gemini-embedding-001", TokenUsageService.roughTokens(query), 0, "HybridRetrievalService");

        List<RetrievedChunk> chunks = chunkSearch.hybridSearch(query, queryEmbedding, effectiveCategories, null);

        // Refusal rule: Refuse if ZERO of the fused top-8 chunks have dense distance <= T.
        boolean shouldRefuse = chunks.stream().noneMatch(c -> c.denseDistance() <= MAX_DENSE_DISTANCE_THRESHOLD);

        return new RetrievalResult(chunks, shouldRefuse);
    }
}
