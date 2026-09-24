package org.hrcopilot.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "source_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourceDocumentEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "source_uri", nullable = false, unique = true)
    private String sourceUri;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
    @Column(name = "pipeline_version", nullable = false)
    private int pipelineVersion;
    @Column(nullable = false)
    private int version = 1;
    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt = Instant.now();

    public SourceDocumentEntity(String sourceUri, String contentHash, int pipelineVersion) {
        this.sourceUri = sourceUri;
        this.contentHash = contentHash;
        this.pipelineVersion = pipelineVersion;
    }

    public void updateContent(String hash, int newPipelineVersion) {
        contentHash = hash;
        pipelineVersion = newPipelineVersion;
        version++;
        ingestedAt = Instant.now();
    }
}
