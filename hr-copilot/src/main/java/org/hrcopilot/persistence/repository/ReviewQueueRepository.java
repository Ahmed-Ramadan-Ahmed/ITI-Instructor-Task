package org.hrcopilot.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.hrcopilot.persistence.entity.ReviewQueueEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewQueueRepository extends JpaRepository<ReviewQueueEntity, UUID> {
    List<ReviewQueueEntity> findByStatusOrderBySubmittedAtAsc(String status);
    List<ReviewQueueEntity> findByRunId(UUID runId);
    boolean existsByRunId(UUID runId);
}
