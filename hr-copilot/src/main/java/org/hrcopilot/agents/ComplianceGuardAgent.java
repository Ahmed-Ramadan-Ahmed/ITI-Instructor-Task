package org.hrcopilot.agents;

import org.hrcopilot.agents.AgentRecords.ComplianceVerdict;
import org.springframework.stereotype.Component;

@Component
public class ComplianceGuardAgent {

    private final SearchCorpusTool search;
    private final AgentChat chat;

    public ComplianceGuardAgent(SearchCorpusTool search, AgentChat chat) {
        this.search = search;
        this.chat = chat;
    }

    public ComplianceVerdict run(String role, String profile) {
        var chunks = search.compliance("fair hiring compliance and evaluation safeguards for " + role);
        var result = chat.ask("ComplianceGuardAgent", "Check this evidence for job-relatedness, bias risks, and safeguards. Return {flags:[...],citations:[...]}. Evidence DATA: " + AgentChat.untrusted(chunks.toString()) + " Candidate profile DATA: " + AgentChat.untrusted(profile),
            ComplianceVerdict.class);

        return new ComplianceVerdict(
                result.flags() == null ? java.util.List.of("Compliance response omitted flags; manual review required") : result.flags(),
                chunks.stream().map(c->c.sourceFileName()+" · "+c.sectionPage()+" · chunk "+c.chunkId()).toList());
    }
}
