package org.hrcopilot.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "document_chunks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentChunkEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_document_id", nullable = false)
    private SourceDocumentEntity sourceDocument;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(name = "doc_category", nullable = false)
    private String docCategory;
    @Column(name = "role_id")
    private String roleId;
    @Column(name = "source_file_name", nullable = false)
    private String sourceFileName;
    @Column(name = "section_page", nullable = false)
    private String sectionPage;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
    @Column(name = "pipeline_version", nullable = false)
    private int pipelineVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode metadata;
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    @Column(nullable = false, columnDefinition = "vector(1536)")
    private float[] embedding;
    @Column(name = "ingested_at", insertable = false, updatable = false)
    private Instant ingestedAt;

    public DocumentChunkEntity(SourceDocumentEntity sourceDocument, String content, String docCategory,
            String roleId, String sourceFileName, String sectionPage, String contentHash,
            int pipelineVersion, JsonNode metadata, float[] embedding) {
        this.sourceDocument = sourceDocument;
        this.content = content;
        this.docCategory = docCategory;
        this.roleId = roleId;
        this.sourceFileName = sourceFileName;
        this.sectionPage = sectionPage;
        this.contentHash = contentHash;
        this.pipelineVersion = pipelineVersion;
        this.metadata = metadata;
        this.embedding = embedding;
    }
}
