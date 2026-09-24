package org.hrcopilot.agents;

import org.hrcopilot.agents.AgentRecords.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class RecommendationAgent {
    private final AgentChat chat;

    public RecommendationAgent(AgentChat chat) {
        this.chat = chat;
    }

    public DecisionDraft run(String candidateId, String runId, RetrievedRequirements requirements, CandidateEvaluation evaluation,
                             ComplianceVerdict compliance, CandidateProfileDetails candidate, MatchAssessment match) {

        var out = chat.ask("RecommendationAgent", "Draft a recommendation for human review. " +
                "Allowed decisions: ADVANCE, HOLD, DECLINE. This is never a final decision. If match score is below threshold, output HOLD and explicitly state that the candidate still requires human review and must not be automatically rejected. " +
                "Carry every compliance flag forward. Return {decision,rationale,citations,flags}. Candidate=" + AgentChat.untrusted(candidate.toString()) + " Match assessment=" + AgentChat.untrusted(match.toString())
                + " Requirements=" + AgentChat.untrusted(requirements.summary()) + " Evaluation=" + AgentChat.untrusted(evaluation.toString()) + " Flags=" + AgentChat.untrusted(compliance.flags().toString()), Draft.class);

        if (!List.of("ADVANCE","HOLD","DECLINE").contains(out.decision())) {
            throw new IllegalArgumentException("invalid decision value");
        }

        var citations = new java.util.LinkedHashSet<String>();

        requirements.chunks().forEach(c->citations.add(c.sourceFileName()+" · "+c.sectionPage()+" · chunk "+c.chunkId()));

        citations.addAll(compliance.citations());

        var flags = new java.util.ArrayList<>(compliance.flags());

        if(!match.thresholdMet()) {
            flags.add("Below " + match.thresholdPercent() + "% triage threshold: human review required; no automatic rejection.");
        }

        String decision=match.thresholdMet()?out.decision():"HOLD";
        String rationale = match.thresholdMet() ?
                out.rationale() :
                "Match score is below the triage threshold; this does not reject the candidate. Human review is required. "+out.rationale();

        return new DecisionDraft(decision,rationale,List.copyOf(citations),List.copyOf(flags),candidateId,runId,candidate,match,evaluation.evidenceSummary());
    }

    public record Draft(String decision, String rationale, List<String> citations, List<String> flags) {}
}
