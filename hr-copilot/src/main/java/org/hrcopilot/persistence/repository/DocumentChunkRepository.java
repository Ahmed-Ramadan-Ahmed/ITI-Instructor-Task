package org.hrcopilot.persistence.repository;

import java.util.UUID;
import org.hrcopilot.persistence.entity.DocumentChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, UUID> {
    long countBySourceDocument_Id(UUID sourceDocumentId);
    void deleteBySourceDocument_Id(UUID sourceDocumentId);
}
