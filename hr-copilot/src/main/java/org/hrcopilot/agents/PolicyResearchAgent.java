package org.hrcopilot.agents;

import org.hrcopilot.agents.AgentRecords.RetrievedRequirements;
import org.springframework.stereotype.Component;

@Component
public class PolicyResearchAgent {

    private static final double MAX_DENSE_DISTANCE = 0.35;
    private final SearchCorpusTool search;

    public PolicyResearchAgent(SearchCorpusTool search) {
        this.search=search;
    }

    public RetrievedRequirements run(String role) {

        var chunks = search.policyRequirements("requirements and rubric for " + role);

        var relevant = chunks.stream()
            .filter(c -> c.denseDistance() <= MAX_DENSE_DISTANCE)
            .filter(c -> !"Introduction".equalsIgnoreCase(c.sectionPage()))
            .toList();

        if(relevant.isEmpty()) {
            throw new IllegalStateException("No sufficiently relevant job-description or rubric evidence was retrieved for role " + role);
        }

        var summary = relevant.stream().map(c -> "[" + c.sourceFileName() + " · " + c.sectionPage() + "]\n" + c.content())
            .collect(java.util.stream.Collectors.joining("\n\n"));

        return new RetrievedRequirements(relevant, summary);
    }
}
