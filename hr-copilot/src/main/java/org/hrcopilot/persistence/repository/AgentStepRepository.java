package org.hrcopilot.persistence.repository;

import java.util.UUID;
import java.util.List;
import org.hrcopilot.persistence.entity.AgentStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentStepRepository extends JpaRepository<AgentStepEntity, UUID> {
    List<AgentStepEntity> findByRunIdOrderByStartedAtAsc(UUID runId);
}
