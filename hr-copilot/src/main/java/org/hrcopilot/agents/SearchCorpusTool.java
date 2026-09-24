package org.hrcopilot.agents;

import org.hrcopilot.model.DocCategory;
import org.hrcopilot.retrieval.HybridRetrievalService;
import org.hrcopilot.retrieval.RetrievedChunk;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.UUID;

/** search_corpus: agent category scope is fixed in code and cannot be widened by model output. */
@Component
public class SearchCorpusTool {
    private final HybridRetrievalService retrieval;

    public SearchCorpusTool(HybridRetrievalService retrieval) {
        this.retrieval = retrieval;
    }

    public java.util.List<RetrievedChunk> policyRequirements(String query) {
        return retrieval.search(
                query, Set.of(
                        DocCategory.JOB_DESCRIPTION.name(),
                        DocCategory.RUBRIC.name()
                ),
                null
        ).chunks();
    }

    public java.util.List<RetrievedChunk> compliance(String query) {
        return retrieval.search(
                query,
                Set.of(
                        DocCategory.COMPLIANCE.name(),
                        DocCategory.COMPANY_POLICY.name(),
                        DocCategory.AI_GOVERNANCE.name()
                ),
                null
        ).chunks();
    }
}