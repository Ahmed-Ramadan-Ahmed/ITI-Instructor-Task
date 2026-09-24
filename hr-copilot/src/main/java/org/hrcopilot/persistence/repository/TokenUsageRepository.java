package org.hrcopilot.persistence.repository;

import java.util.UUID;
import java.util.List;
import org.hrcopilot.persistence.entity.TokenUsageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenUsageRepository extends JpaRepository<TokenUsageEntity, UUID> {
    List<TokenUsageEntity> findByRunIdOrderByCreatedAtAsc(UUID runId);
}
