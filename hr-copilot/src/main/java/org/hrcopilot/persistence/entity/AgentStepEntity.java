package org.hrcopilot.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "agent_steps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentStepEntity {
    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "agent_name", nullable = false, length = 64)
    private String agentName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode input;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_json", columnDefinition = "jsonb")
    private JsonNode output;

    @Column(name = "started_at", insertable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(columnDefinition = "text")
    private String error;

    public AgentStepEntity(UUID id, UUID runId, String agentName, JsonNode input) {
        this.id = id;
        this.runId = runId;
        this.agentName = agentName;
        this.input = input;
        this.status = "RUNNING";
    }

    public void complete(JsonNode output) {
        this.output = output;
        this.status = "COMPLETED";
        this.completedAt = Instant.now();
    }

    public void fail(String message) {
        this.status = "FAILED";
        this.error = message;
        this.completedAt = Instant.now();
    }
}
