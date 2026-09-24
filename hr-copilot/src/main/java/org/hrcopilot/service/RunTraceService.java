package org.hrcopilot.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hrcopilot.persistence.entity.AgentRunEntity;
import org.hrcopilot.persistence.entity.AgentStepEntity;
import org.hrcopilot.persistence.entity.ReviewQueueEntity;
import org.hrcopilot.persistence.entity.TokenUsageEntity;
import org.hrcopilot.persistence.repository.AgentRunRepository;
import org.hrcopilot.persistence.repository.AgentStepRepository;
import org.hrcopilot.persistence.repository.ReviewQueueRepository;
import org.hrcopilot.persistence.repository.TokenUsageRepository;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RunTraceService {
    private final AgentRunRepository runs;
    private final AgentStepRepository steps;
    private final TokenUsageRepository usage;
    private final ReviewQueueRepository reviews;

    public Map<String, Object> getTrace(UUID runId) {
        AgentRunEntity run = runs.findById(runId).orElse(null);
        var reviewRows = reviews.findByRunId(runId);
        ReviewQueueEntity review = reviewRows.isEmpty() ? null : reviewRows.getFirst();

        Map<String, Object> trace = runSummary(runId, run, review);
        trace.put("steps", steps.findByRunIdOrderByStartedAtAsc(runId).stream()
                .map(this::stepSummary).toList());
        trace.put("tokenUsage", usage.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .map(this::usageSummary).toList());
        trace.put("review", review == null ? java.util.List.of() : java.util.List.of(reviewSummary(review)));
        return trace;
    }

    private Map<String, Object> runSummary(UUID runId, AgentRunEntity run, ReviewQueueEntity review) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", runId);
        if (run == null) return summary;
        summary.put("candidate_id", run.getCandidateId());
        summary.put("submitted_by", run.getSubmittedBy());
        summary.put("current_state", run.getCurrentState());
        summary.put("started_at", run.getStartedAt());
        summary.put("completed_at", run.getCompletedAt() != null
                ? run.getCompletedAt() : review == null ? null : review.getSubmittedAt());
        String outcome = run.getOutcome();
        if (outcome == null && review != null && review.getStatus().equals("PENDING")) {
            outcome = "PENDING_REVIEW";
        }
        summary.put("outcome", outcome);
        return summary;
    }

    private Map<String, Object> stepSummary(AgentStepEntity step) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("agent_name", step.getAgentName());
        summary.put("status", step.getStatus());
        summary.put("input_json", jsonString(step.getInput()));
        summary.put("output_json", jsonString(step.getOutput()));
        summary.put("error", step.getError());
        summary.put("started_at", step.getStartedAt());
        summary.put("completed_at", step.getCompletedAt());
        return summary;
    }

    private Map<String, Object> usageSummary(TokenUsageEntity row) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("source", row.getSource());
        summary.put("kind", row.getKind());
        summary.put("agent_name", row.getAgentName());
        summary.put("model", row.getModel());
        summary.put("prompt_tokens", row.getPromptTokens());
        summary.put("completion_tokens", row.getCompletionTokens());
        summary.put("estimated_cost_usd", row.getEstimatedCostUsd());
        summary.put("created_at", row.getCreatedAt());
        return summary;
    }

    private Map<String, Object> reviewSummary(ReviewQueueEntity review) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", review.getId());
        summary.put("status", review.getStatus());
        summary.put("decision_draft", jsonString(review.getDecisionDraft()));
        summary.put("edited_payload", jsonString(review.getEditedPayload()));
        summary.put("reviewer_id", review.getReviewerId());
        summary.put("decided_at", review.getDecidedAt());
        summary.put("executed_at", review.getExecutedAt());
        return summary;
    }

    private String jsonString(com.fasterxml.jackson.databind.JsonNode node) {
        return node == null ? null : node.toString();
    }
}
