package org.hrcopilot.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "candidate_screening_decisions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandidateScreeningDecisionEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_id", nullable = false, unique = true)
    private UUID runId;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(nullable = false, length = 16)
    private String decision;

    @Column(nullable = false, columnDefinition = "text")
    private String rationale;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "citations_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode citations;

    @Column(name = "decided_at", insertable = false, updatable = false)
    private Instant decidedAt;

    public CandidateScreeningDecisionEntity(UUID runId, UUID candidateId, String decision,
                                            String rationale, JsonNode citations) {
        this.runId = runId;
        this.candidateId = candidateId;
        this.decision = decision;
        this.rationale = rationale;
        this.citations = citations;
    }
}
