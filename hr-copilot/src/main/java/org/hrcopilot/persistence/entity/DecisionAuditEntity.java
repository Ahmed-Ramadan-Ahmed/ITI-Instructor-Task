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
@Table(name = "decision_audit")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DecisionAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "review_queue_id", nullable = false)
    private UUID reviewQueueId;

    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;

    @Column(nullable = false, length = 16)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_payload", columnDefinition = "jsonb")
    private JsonNode beforePayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_payload", columnDefinition = "jsonb")
    private JsonNode afterPayload;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public DecisionAuditEntity(UUID reviewQueueId, UUID reviewerId, String action,
                               JsonNode beforePayload, JsonNode afterPayload, String reason) {
        this.reviewQueueId = reviewQueueId;
        this.reviewerId = reviewerId;
        this.action = action;
        this.beforePayload = beforePayload;
        this.afterPayload = afterPayload;
        this.reason = reason;
    }
}
