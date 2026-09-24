package org.hrcopilot.persistence.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.hrcopilot.persistence.entity.AgentRunEntity;
import org.hrcopilot.persistence.entity.AgentStepEntity;
import org.hrcopilot.persistence.entity.CandidateEntity;
import org.hrcopilot.persistence.entity.ReviewQueueEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

/** Coordinates the JPA repositories used by the screening state machine. */
@Repository
@Transactional
@RequiredArgsConstructor
public class ScreeningWorkflowRepository {
    private final UserRepository users;
    private final CandidateRepository candidates;
    private final AgentRunRepository runs;
    private final AgentStepRepository steps;
    private final ReviewQueueRepository reviews;
    private final ObjectMapper mapper;

    public UUID userId(String username) {
        return users.findByUsername(username).orElseThrow().getId();
    }

    public void createCandidateAndRun(UUID candidateId, UUID runId, String name,
                                      String profileJson, String role, UUID userId) {
        candidates.save(new CandidateEntity(candidateId, name, parseJson(profileJson), role));
        runs.save(new AgentRunEntity(runId, candidateId, userId, "SUBMITTED"));
    }

    public void startStep(UUID stepId, UUID runId, String agentName) {
        steps.save(new AgentStepEntity(stepId, runId, agentName, mapper.createObjectNode()));
    }

    public void completeStep(UUID stepId, String outputJson) {
        AgentStepEntity step = findStep(stepId);
        step.complete(parseJson(outputJson));
        steps.save(step);
    }

    public void failStep(UUID stepId, String error) {
        AgentStepEntity step = findStep(stepId);
        step.fail(error);
        steps.save(step);
    }

    public void updateRunState(UUID runId, String state) {
        AgentRunEntity run = findRun(runId);
        run.changeState(state);
        runs.save(run);
    }

    public void queueReviewDraft(UUID runId, String draftJson) {
        if (!reviews.existsByRunId(runId)) {
            reviews.save(new ReviewQueueEntity(runId, parseJson(draftJson)));
        }
        AgentRunEntity run = findRun(runId);
        run.complete("PENDING_APPROVAL", "PENDING_REVIEW");
        runs.save(run);
    }

    private AgentStepEntity findStep(UUID stepId) {
        return steps.findById(stepId).orElseThrow(() -> new IllegalStateException("Agent step was not found"));
    }

    private AgentRunEntity findRun(UUID runId) {
        return runs.findById(runId).orElseThrow(() -> new IllegalStateException("Screening run was not found"));
    }

    private com.fasterxml.jackson.databind.JsonNode parseJson(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid JSON persistence payload", exception);
        }
    }
}
