package org.hrcopilot.agents;
import org.hrcopilot.agents.AgentRecords.DecisionDraft;
import org.springframework.stereotype.Component;
import java.util.Set;

/** draft_decision: validates the recommendation schema before queueing it for review. */
@Component
public class DraftDecisionTool {
    public DecisionDraft validate(DecisionDraft draft) {

        if(draft == null || !Set.of("ADVANCE","HOLD","DECLINE").contains(draft.decision())
                || draft.rationale()==null || draft.rationale().isBlank()
                || draft.candidateId() == null || draft.runId() == null) {

            throw new IllegalArgumentException("invalid decision draft");
        }

        if(draft.match() != null &&
                (draft.match().percent() < 0 || draft.match().percent() > 100
                        || draft.match().thresholdPercent() < 1 || draft.match().thresholdPercent() > 100)) {

            throw new IllegalArgumentException("invalid candidate match score");
        }

        return draft;
    }
}
