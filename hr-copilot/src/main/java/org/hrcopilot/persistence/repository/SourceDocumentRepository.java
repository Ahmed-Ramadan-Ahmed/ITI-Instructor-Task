package org.hrcopilot.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.hrcopilot.persistence.entity.SourceDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceDocumentRepository extends JpaRepository<SourceDocumentEntity, UUID> {
    Optional<SourceDocumentEntity> findBySourceUri(String sourceUri);
}
