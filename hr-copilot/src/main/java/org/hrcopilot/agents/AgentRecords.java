package org.hrcopilot.agents;

import java.util.List;
import java.util.Map;
import org.hrcopilot.retrieval.RetrievedChunk;
import lombok.Builder;

public final class AgentRecords {

    private AgentRecords() {}

    public record RetrievedRequirements(List<RetrievedChunk> chunks, String summary) {}

    public record ComplianceVerdict(List<String> flags, List<String> citations) {}

    public record CandidateEvaluation(Map<String, Integer> scores, String evidenceSummary, MatchAssessment matchAssessment) {}

    public record CandidateProfileDetails(String name, String roleApplied, String education, double yearsExperience,
                                          boolean freshmanAssumed, String experienceBasis) {}

    public record MatchAssessment(int percent, int thresholdPercent, boolean thresholdMet,
                                  Map<String,Integer> criterionScores, List<String> missingCriteria, String interpretation) {}

    @Builder
    public record DecisionDraft(String decision, String rationale, List<String> citations, List<String> flags,
                                String candidateId, String runId, CandidateProfileDetails candidate,
                                MatchAssessment match, String evidenceSummary) {}

    public record CandidateInput(String name, String roleApplied, String profileJson) {}
}
