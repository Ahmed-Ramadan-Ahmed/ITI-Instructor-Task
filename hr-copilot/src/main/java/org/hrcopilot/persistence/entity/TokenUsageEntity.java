package org.hrcopilot.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "token_usage")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TokenUsageEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "run_id")
    private UUID runId;
    @Column(nullable = false)
    private String source;
    @Column(nullable = false)
    private String kind;
    @Column(name = "agent_name")
    private String agentName;
    @Column(nullable = false)
    private String model;
    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;
    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;
    @Column(name = "estimated_cost_usd", nullable = false, precision = 10, scale = 6)
    private BigDecimal estimatedCostUsd;
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public TokenUsageEntity(UUID runId, String source, String kind, String agentName, String model,
                            int promptTokens, int completionTokens, BigDecimal estimatedCostUsd) {
        this.runId = runId;
        this.source = source;
        this.kind = kind;
        this.agentName = agentName;
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.estimatedCostUsd = estimatedCostUsd;
    }

}
