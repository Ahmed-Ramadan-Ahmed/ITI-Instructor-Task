package org.hrcopilot.persistence.repository;

import java.util.UUID;
import org.hrcopilot.persistence.entity.CandidateScreeningDecisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateScreeningDecisionRepository extends JpaRepository<CandidateScreeningDecisionEntity, UUID> {
    boolean existsByRunId(UUID runId);
}
