package org.hrcopilot.persistence.repository;

import java.util.UUID;
import org.hrcopilot.persistence.entity.CandidateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateRepository extends JpaRepository<CandidateEntity, UUID> { }
