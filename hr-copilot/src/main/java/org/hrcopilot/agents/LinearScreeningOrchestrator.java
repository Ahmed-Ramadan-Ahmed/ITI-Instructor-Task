package org.hrcopilot.agents;

import java.util.UUID;
import org.hrcopilot.agents.AgentRecords.CandidateInput;
import org.hrcopilot.persistence.repository.ScreeningWorkflowRepository;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/** Creates screening records and dispatches work to the asynchronous screening worker. */
@Service
@RequiredArgsConstructor
public class LinearScreeningOrchestrator {

    private final ScreeningWorkflowRepository workflowRepository;
    private final ScreeningWorkflowWorker workflowWorker;

    public UUID submit(CandidateInput candidate, String username) {
        UUID candidateId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID userId = workflowRepository.userId(username);

        workflowRepository.createCandidateAndRun(candidateId, runId, candidate.name(),
                candidate.profileJson(), candidate.roleApplied(), userId);

        workflowWorker.process(runId, candidateId, candidate.name(), candidate.roleApplied(),
                candidate.profileJson());

        return runId;
    }
}
