package org.hrcopilot.persistence.repository;

import java.util.UUID;
import org.hrcopilot.persistence.entity.AgentRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunRepository extends JpaRepository<AgentRunEntity, UUID> { }
