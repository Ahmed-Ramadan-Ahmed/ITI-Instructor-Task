package org.hrcopilot.persistence.repository;

import java.util.UUID;
import org.hrcopilot.persistence.entity.DecisionAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionAuditRepository extends JpaRepository<DecisionAuditEntity, UUID> { }
