package org.hrcopilot.persistence.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hrcopilot.persistence.entity.AgentRunEntity;
import org.hrcopilot.persistence.entity.CandidateScreeningDecisionEntity;
import org.hrcopilot.persistence.entity.DecisionAuditEntity;
import org.hrcopilot.persistence.entity.ReviewQueueEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

/** JPA persistence operations for review decisions and their audit trail. */
@Repository
@Transactional
@RequiredArgsConstructor
public class ReviewWorkflowRepository {
    private final UserRepository users;
    private final ReviewQueueRepository reviews;
    private final AgentRunRepository runs;
    private final DecisionAuditRepository audits;
    private final CandidateScreeningDecisionRepository decisions;
    private final ObjectMapper mapper;

    public UUID findReviewerId(String username) {
        return users.findByUsername(username).orElseThrow().getId();
    }

    public List<Map<String, Object>> pendingReviews() {
        return reviews.findByStatusOrderBySubmittedAtAsc("PENDING").stream()
                .map(this::toPendingReviewRow)
                .toList();
    }

    public String findPendingDraft(UUID reviewId) {
        ReviewQueueEntity review = findReview(reviewId);
        if (!review.getStatus().equals("PENDING"))
            throw new IllegalStateException("review is no longer pending");
        return review.getDecisionDraft().toString();
    }

    public int decidePending(UUID id, UUID reviewerId, String status,
                             String editedPayload, String rejectReason) {
        ReviewQueueEntity review = findReview(id);
        if (!review.getStatus().equals("PENDING")) return 0;
        review.decide(status, reviewerId, readJsonOrNull(editedPayload), rejectReason);
        reviews.save(review);
        return 1;
    }

    public void insertAudit(UUID reviewId, UUID reviewerId, String action,
                            String before, String after, String reason) {
        audits.save(new DecisionAuditEntity(reviewId, reviewerId, action,
                readJsonOrNull(before), readJsonOrNull(after), reason));
    }

    public void updateRunAfterReview(UUID reviewId, String state, String action) {
        ReviewQueueEntity review = findReview(reviewId);
        AgentRunEntity run = findRun(review.getRunId());

        if (state.equals("COMPLETED")) run.complete(state, action);
        else run.changeState(state);

        runs.save(run);
    }

    public int markReadyForExecution(UUID reviewId) {
        ReviewQueueEntity review = findReview(reviewId);
        if (!List.of("APPROVED", "EDITED").contains(review.getStatus())) return 0;
        review.markExecuted();
        reviews.save(review);
        return 1;
    }

    public boolean hasExecutedDecision(UUID reviewId) {
        ReviewQueueEntity review = findReview(reviewId);
        return review.getStatus().equals("EXECUTED") && decisions.existsByRunId(review.getRunId());
    }

    public void markRunExecuting(UUID reviewId) {
        ReviewQueueEntity review = findReview(reviewId);
        AgentRunEntity run = findRun(review.getRunId());
        run.changeState("EXECUTING");
        runs.save(run);
    }

    public Map<String, Object> executionData(UUID reviewId) {
        ReviewQueueEntity review = findReview(reviewId);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("run_id", review.getRunId());
        row.put("edited_payload", jsonString(review.getEditedPayload()));
        row.put("decision_draft", jsonString(review.getDecisionDraft()));
        return row;
    }

    public UUID candidateForRun(UUID runId) {
        return findRun(runId).getCandidateId();
    }

    public int insertDecision(UUID runId, UUID candidateId, String decision,
                              String rationale, String citationsJson) {
        if (decisions.existsByRunId(runId)) return 0;
        decisions.save(new CandidateScreeningDecisionEntity(runId, candidateId,
                decision, rationale, readJson(citationsJson)));
        return 1;
    }

    public void completeRun(UUID runId, String decision) {
        AgentRunEntity run = findRun(runId);
        run.complete("COMPLETED", decision);
        runs.save(run);
    }

    private Map<String, Object> toPendingReviewRow(ReviewQueueEntity review) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", review.getId());
        row.put("run_id", review.getRunId());
        row.put("decision_draft", jsonString(review.getDecisionDraft()));
        row.put("edited_payload", jsonString(review.getEditedPayload()));
        row.put("status", review.getStatus());
        row.put("submitted_at", review.getSubmittedAt());
        return row;
    }

    private ReviewQueueEntity findReview(UUID reviewId) {
        return reviews.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("review was not found"));
    }

    private AgentRunEntity findRun(UUID runId) {
        return runs.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("screening run was not found"));
    }

    private JsonNode readJson(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("invalid JSON persistence payload", exception);
        }
    }

    private JsonNode readJsonOrNull(String json) {
        return json == null ? null : readJson(json);
    }

    private String jsonString(JsonNode json) {
        return json == null ? null : json.toString();
    }
}
