package org.hrcopilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hrcopilot.persistence.repository.ReviewWorkflowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ObjectMapper mapper;
    private final ReviewWorkflowRepository workflow;

    public List<Map<String, Object>> pendingReviews() {
        return workflow.pendingReviews();
    }

    @Transactional
    public void decide(UUID id, String username, String action, String reason, String edited) {
        UUID reviewerId = workflow.findReviewerId(username);
        String originalDraft = workflow.findPendingDraft(id);
        String status = statusFor(action);
        String effectivePayload = action.equals("EDITED") ? edited : originalDraft;
        validate(effectivePayload);
        updateReview(id, reviewerId, status, action, reason, originalDraft, effectivePayload);
        updateRunAfterReview(id, status, action);
    }

    private String statusFor(String action) {
        return switch (action) {
            case "REJECTED" -> "REJECTED";
            case "EDITED" -> "EDITED";
            case "APPROVED" -> "APPROVED";
            default -> throw new IllegalArgumentException("unsupported review action");
        };
    }

    private void updateReview(UUID id, UUID reviewerId, String status, String action,
                              String reason, String originalDraft, String effectivePayload) {
        int updated = workflow.decidePending(id, reviewerId, status,
                action.equals("EDITED") ? effectivePayload : null,
                action.equals("REJECTED") ? reason : null);
        if (updated != 1) throw new IllegalStateException("review is no longer pending");

        try {
            workflow.insertAudit(id, reviewerId, action, originalDraft,
                    action.equals("REJECTED") ? null : effectivePayload, reason);
        }
        catch (Exception exception) {
            throw new IllegalStateException("review audit write failed", exception);
        }
    }

    private void updateRunAfterReview(UUID id, String status, String action) {
        String state = status.equals("REJECTED") ? "COMPLETED" : "APPROVED";
        workflow.updateRunAfterReview(id, state, action);
    }

    @Transactional
    public int execute(UUID id) {
        if (workflow.markReadyForExecution(id) == 0) return resolveAlreadyExecuted(id);
        workflow.markRunExecuting(id);
        Map<String, Object> row = workflow.executionData(id);
        String payload = effectivePayload(row);
        return saveDecision(row, payload);
    }

    private int resolveAlreadyExecuted(UUID reviewId) {
        if (workflow.hasExecutedDecision(reviewId)) return 0;
        throw new IllegalStateException("not approved");
    }

    private String effectivePayload(Map<String, Object> row) {
        Object edited = row.get("edited_payload");
        Object draft = row.get("decision_draft");
        String payload = (edited != null ? edited : draft).toString();
        validate(payload);
        return payload;
    }

    private int saveDecision(Map<String, Object> row, String payload) {
        try {
            JsonNode decision = mapper.readTree(payload);
            UUID runId = UUID.fromString(decision.path("runId").asText());
            UUID candidateId = UUID.fromString(decision.path("candidateId").asText());
            UUID expectedRunId = (UUID) row.get("run_id");
            if (!runId.equals(expectedRunId)) throw new IllegalArgumentException("run identity changed");
            if (!candidateId.equals(workflow.candidateForRun(runId))) {
                throw new IllegalArgumentException("candidate identity changed");
            }
            int inserted = workflow.insertDecision(runId, candidateId,
                    decision.path("decision").asText(), decision.path("rationale").asText(),
                    decision.path("citations").toString());
            workflow.completeRun(runId, decision.path("decision").asText());
            return inserted;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid decision payload", exception);
        }
    }

    private void validate(String value) {
        try {
            JsonNode node = mapper.readTree(value);
            boolean validDecision = node.path("decision").asText().matches("ADVANCE|HOLD|DECLINE");
            if (!validDecision || node.path("rationale").asText().isBlank()
                    || node.path("candidateId").asText().isBlank()
                    || node.path("runId").asText().isBlank()) {
                throw new IllegalArgumentException("invalid decision payload");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid decision payload", exception);
        }
    }
}
