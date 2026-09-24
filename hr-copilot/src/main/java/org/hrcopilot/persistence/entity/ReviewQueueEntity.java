package org.hrcopilot.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "review_queue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewQueueEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "run_id", nullable = false, unique = true)
    private UUID runId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "decision_draft", nullable = false, columnDefinition = "jsonb")
    private JsonNode decisionDraft;
    @Column(nullable = false, length = 20)
    private String status = "PENDING";
    @Column(name = "submitted_at", insertable = false, updatable = false)
    private Instant submittedAt;
    @Column(name = "reviewer_id")
    private UUID reviewerId;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "executed_at")
    private Instant executedAt;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "edited_payload", columnDefinition = "jsonb")
    private JsonNode editedPayload;
    @Column(name = "reject_reason", columnDefinition = "text")
    private String rejectReason;
    @Version
    @Column(nullable = false)
    private int version;

    public ReviewQueueEntity(UUID runId, JsonNode decisionDraft) {
        this.runId = runId;
        this.decisionDraft = decisionDraft;
    }

    public void decide(String nextStatus, UUID reviewer, JsonNode edited, String rejectionReason) {
        status = nextStatus;
        reviewerId = reviewer;
        decidedAt = Instant.now();
        editedPayload = edited;
        rejectReason = rejectionReason;
    }

    public void markExecuted() { status = "EXECUTED"; executedAt = Instant.now(); }
}
