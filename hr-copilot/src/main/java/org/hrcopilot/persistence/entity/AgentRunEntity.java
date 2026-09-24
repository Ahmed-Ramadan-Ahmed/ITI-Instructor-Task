package org.hrcopilot.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "agent_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentRunEntity {
    @Id
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "current_state", nullable = false, length = 32)
    private String currentState;

    @Column(name = "started_at", insertable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(length = 32)
    private String outcome;

    @Column(name = "last_heartbeat_at", nullable = false, insertable = false, updatable = false)
    private Instant lastHeartbeatAt;

    @Column(name = "agent_deadline_ms", nullable = false)
    private long agentDeadlineMs = 600_000;

    @Column(name = "agent_elapsed_ms", nullable = false)
    private long agentElapsedMs;

    @Version
    @Column(nullable = false)
    private Integer version;

    public AgentRunEntity(UUID id, UUID candidateId, UUID submittedBy, String currentState) {
        this.id = id;
        this.candidateId = candidateId;
        this.submittedBy = submittedBy;
        this.currentState = currentState;
    }

    public void changeState(String state) {
        this.currentState = state;
    }

    public void complete(String state, String result) {
        this.currentState = state;
        this.outcome = result;
        this.completedAt = Instant.now();
    }
}
