package org.hrcopilot.agents;

import org.hrcopilot.agents.AgentRecords.CandidateEvaluation;
import org.springframework.stereotype.Component;

@Component
public class CandidateEvaluatorAgent {

    private final AgentChat chat;
    private final MatchAssessmentService scorer;

    public CandidateEvaluatorAgent(AgentChat chat, MatchAssessmentService scorer) {
        this.chat=chat; this.scorer=scorer;
    }

    public CandidateEvaluation run(String role, String profile, String requirements) {

        String criteria = switch(role) {
            case "Software Engineer" -> "Problem solving, Programming fundamentals, Testing practice, Collaboration";
            case "People Analyst" -> "Data reasoning, Spreadsheet skills, Communication, Privacy practice";
            case "HR Coordinator" -> "Organization, Written communication, Confidential handling, Service judgment";
            default -> throw new IllegalArgumentException("Unsupported screening role: " + role);
        };

        var base = chat.ask(
                "CandidateEvaluatorAgent",
                "Assess this resume only against documented role requirements. " +
                        "Return one integer score from 0 (no resume evidence) to 5 (strong direct evidence) for EVERY criterion, using these exact keys: " + criteria +
                        ". Missing resume evidence is 0, not an invented qualification. Do not score education prestige, age, names, protected traits, or years of experience unless explicitly job-required. " +
                        "Return {scores:{criterion:integer},evidenceSummary:string}. Role=" + AgentChat.untrusted(role) +
                        " Profile DATA: " + AgentChat.untrusted(profile) + " Requirements DATA: " + AgentChat.untrusted(requirements),
            BaseEvaluation.class);

        return new CandidateEvaluation(
                base.scores(),
                base.evidenceSummary(),
                scorer.assess(
                        role,
                        new CandidateEvaluation(base.scores(),
                                base.evidenceSummary(),
                                null)
                )
        );
    }

    public record BaseEvaluation(java.util.Map<String,Integer> scores,String evidenceSummary) {}
}
