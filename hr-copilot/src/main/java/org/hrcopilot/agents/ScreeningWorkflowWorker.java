package org.hrcopilot.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.hrcopilot.agents.AgentRecords.CandidateEvaluation;
import org.hrcopilot.agents.AgentRecords.CandidateProfileDetails;
import org.hrcopilot.agents.AgentRecords.ComplianceVerdict;
import org.hrcopilot.agents.AgentRecords.DecisionDraft;
import org.hrcopilot.agents.AgentRecords.RetrievedRequirements;
import org.hrcopilot.persistence.repository.ScreeningWorkflowRepository;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Runs the specialist-agent stages and persists the resulting reviewer draft. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScreeningWorkflowWorker {

    private static final int AGENT_TIMEOUT_SECONDS = 30;

    private final ScreeningWorkflowRepository workflowRepository;
    private final ObjectMapper mapper;
    private final PolicyResearchAgent researchAgent;
    private final ComplianceGuardAgent complianceAgent;
    private final CandidateEvaluatorAgent evaluator;
    private final RecommendationAgent recommender;
    private final DraftDecisionTool draftTool;
    private final CandidateProfileAnalyzer profileAnalyzer;

    @Async("screeningExecutor")
    public void process(UUID runId, UUID candidateId, String name, String role, String profile) {

        setCorrelation(runId);
        CandidateProfileDetails candidateDetails = null;

        try {
            candidateDetails = profileAnalyzer.analyze(name, role, profile);
            DecisionDraft draft = runAgentStages(runId, candidateId, role, profile, candidateDetails);
            queueDraft(runId, draft);
        }
        catch (Exception failure) {
            log.warn("Automated screening failed for run {} ({})",
                    runId, failure.getClass().getSimpleName());
            persistFallback(runId, candidateId, candidateDetails);
        }
        finally {
            clearCorrelation();
        }
    }

    private DecisionDraft runAgentStages(UUID runId, UUID candidateId, String role,
                                         String profile, CandidateProfileDetails candidateDetails) throws Exception {
        state(runId, "RESEARCHING");
        RetrievedRequirements requirements = timed("PolicyResearchAgent", runId,
                () -> researchAgent.run(role));

        state(runId, "COMPLIANCE_CHECKING");
        ComplianceVerdict verdict = timed("ComplianceGuardAgent", runId,
                () -> complianceAgent.run(role, profile));

        state(runId, "EVALUATING");
        CandidateEvaluation evaluation = timed("CandidateEvaluatorAgent", runId,
                () -> evaluator.run(role, profile, requirements.summary()));

        state(runId, "DRAFTING_DECISION");
        return timed("RecommendationAgent", runId, () -> draftTool.validate(recommender.run(
                candidateId.toString(), runId.toString(), requirements, evaluation, verdict,
                candidateDetails, evaluation.matchAssessment())));
    }

    private <T> T timed(String agentName, UUID runId, Supplier<T> work) throws Exception {
        UUID stepId = UUID.randomUUID();
        workflowRepository.startStep(stepId, runId, agentName);
        try {
            T result = invokeWithTimeout(runId, work);
            workflowRepository.completeStep(stepId, mapper.writeValueAsString(result));
            return result;
        }
        catch (Exception exception) {
            workflowRepository.failStep(stepId, exception.getMessage());
            throw exception;
        }
    }

    private <T> T invokeWithTimeout(UUID runId, Supplier<T> work) throws Exception {
        return CompletableFuture.supplyAsync(() -> {
            setCorrelation(runId);
            try {
                return work.get();
            }
            finally {
                clearCorrelation();
            }
        }).get(AGENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void persistFallback(UUID runId, UUID candidateId, CandidateProfileDetails candidateDetails) {
        try {
            state(runId, "FALLBACK");
            DecisionDraft fallback = DecisionDraft.builder()
                    .decision("HOLD")
                    .rationale("Automated assessment did not complete, so no candidate recommendation was produced. "
                            + "A reviewer must assess the profile against the cited role rubric before taking any action.")
                    .citations(List.of())
                    .flags(List.of("Manual review required; inspect the failed step in this run trace."))
                    .candidateId(candidateId.toString())
                    .runId(runId.toString())
                    .candidate(candidateDetails)
                    .build();
            queueDraft(runId, fallback);
        }
        catch (Exception persistenceFailure) {
            throw new IllegalStateException("Could not persist fallback review draft", persistenceFailure);
        }
    }

    private void queueDraft(UUID runId, DecisionDraft draft) {
        try {
            workflowRepository.queueReviewDraft(runId, mapper.writeValueAsString(draft));
        }
        catch (Exception exception) {
            throw new IllegalStateException("Could not persist review draft", exception);
        }
    }

    private void state(UUID runId, String value) {
        workflowRepository.updateRunState(runId, value);
    }

    private void setCorrelation(UUID runId) {
        MDC.put("runId", runId.toString());
        MDC.put("source", "SCREENING");
    }

    private void clearCorrelation() {
        MDC.remove("runId");
        MDC.remove("source");
    }
}
