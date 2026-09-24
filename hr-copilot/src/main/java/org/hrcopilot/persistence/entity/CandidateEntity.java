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
import lombok.NoArgsConstructor;

@Entity
@Table(name = "candidates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandidateEntity {
    @Id
    private UUID id;

    @Column(nullable = false, columnDefinition = "text")
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_profile_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode rawProfile;

    @Column(name = "role_applied", nullable = false, length = 32)
    private String roleApplied;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public CandidateEntity(UUID id, String name, JsonNode rawProfile, String roleApplied) {
        this.id = id;
        this.name = name;
        this.rawProfile = rawProfile;
        this.roleApplied = roleApplied;
    }
}
