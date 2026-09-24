package org.hrcopilot.agents;

import org.hrcopilot.agents.AgentRecords.CandidateEvaluation;
import org.hrcopilot.agents.AgentRecords.MatchAssessment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Produces a consistent 0-100 evidence-match indicator for reviewer triage, never an employment action. */
@Service
public class MatchAssessmentService {

    private static final Map<String,List<String>> CRITERIA=Map.of(
        "Software Engineer", List.of("Problem solving","Programming fundamentals","Testing practice","Collaboration"),
        "People Analyst", List.of("Data reasoning","Spreadsheet skills","Communication","Privacy practice"),
        "HR Coordinator", List.of("Organization","Written communication","Confidential handling","Service judgment")
    );

    private final int threshold;

    public MatchAssessmentService(@Value("${app.screening.match-threshold-percent:70}") int threshold) {
        if(threshold<1 || threshold>100) {
            throw new IllegalArgumentException("app.screening.match-threshold-percent must be between 1 and 100");
        }
        this.threshold=threshold;
    }

    public MatchAssessment assess(String role, CandidateEvaluation evaluation) {
        List<String> required=CRITERIA.get(role);

        if(required==null) {
            throw new IllegalArgumentException("No screening rubric is configured for role: " + role);
        }

        Map<String,Integer> source=evaluation.scores() == null ? Map.of() : evaluation.scores();

        Map<String,Integer> normalized = new LinkedHashMap<>();
        java.util.List<String> missing = new java.util.ArrayList<>();

        int total=0;
        for(String criterion:required) {
            Integer raw=source.get(criterion);

            if(raw==null)
                missing.add(criterion);

            int score=raw==null?0:Math.max(0,Math.min(5,raw));

            normalized.put(criterion,score);

            total+=score;
        }

        int percent = (int) Math.round(total*100.0/(required.size()*5));

        boolean meets = (percent >= threshold);

        String interpretation=meets?"At or above the review threshold; reviewer verification is still required."
            :"Below the review threshold; send to human review. Score alone must not reject a candidate.";

        if(!missing.isEmpty()) {
            interpretation += " The evaluation omitted criteria: " + String.join(", ", missing) + "; those criteria scored 0 and need reviewer verification.";
        }

        return new MatchAssessment(
                percent,
                threshold,
                meets,
                java.util.Collections.unmodifiableMap(normalized),
                java.util.List.copyOf(missing),
                interpretation
        );
    }
}
