package org.hrcopilot.ingestion;

import org.hrcopilot.ingestion.ChunkEmbedder.ChunkWithEmbedding;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ingestion idempotency per plan §4.3:
 * - Sequential double-ingest → unchanged row counts
 * - Content change → old chunks replaced, version incremented
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "spring.ai.google.genai.api-key=${GEMINI_API_KEY:test-key}"
})
public class IngestionIdempotencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("hr_copilot")
            .withUsername("hr_copilot")
            .withPassword("hr_copilot");

    @Autowired
    private ChunkIndexer chunkIndexer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final float[] DUMMY_EMBEDDING = createDummyEmbedding(1536);

    private static float[] createDummyEmbedding(int dims) {
        float[] embedding = new float[dims];
        for (int i = 0; i < dims; i++) {
            embedding[i] = (float) Math.sin(i * 0.1);
        }
        return embedding;
    }

    @Test
    void sequentialDoubleIngest_unchangedRowCounts() {
        String sourceUri = "file://test-idempotent.md";
        String contentHash = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        int pipelineVersion = 1;

        List<ChunkWithEmbedding> chunks = List.of(
            new ChunkWithEmbedding(
                new Document("Chunk 1 content about HR policies", Map.of("source", "test-idempotent.md", "section", "Overview")),
                DUMMY_EMBEDDING
            ),
            new ChunkWithEmbedding(
                new Document("Chunk 2 content about compliance", Map.of("source", "test-idempotent.md", "section", "Compliance")),
                DUMMY_EMBEDDING
            )
        );

        // First ingest
        chunkIndexer.index(sourceUri, contentHash, pipelineVersion, "COMPANY_POLICY", null, "test-idempotent.md", chunks);

        Integer docCount1 = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM source_documents WHERE source_uri = ?", Integer.class, sourceUri);
        Integer chunkCount1 = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM document_chunks dc JOIN source_documents sd ON dc.source_document_id = sd.id WHERE sd.source_uri = ?", 
            Integer.class, sourceUri);
        Integer version1 = jdbcTemplate.queryForObject(
            "SELECT version FROM source_documents WHERE source_uri = ?", Integer.class, sourceUri);

        assertThat(docCount1).isEqualTo(1);
        assertThat(chunkCount1).isEqualTo(2);
        assertThat(version1).isEqualTo(1);

        // Second ingest — same content, same pipeline version
        chunkIndexer.index(sourceUri, contentHash, pipelineVersion, "COMPANY_POLICY", null, "test-idempotent.md", chunks);

        Integer docCount2 = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM source_documents WHERE source_uri = ?", Integer.class, sourceUri);
        Integer chunkCount2 = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM document_chunks dc JOIN source_documents sd ON dc.source_document_id = sd.id WHERE sd.source_uri = ?", 
            Integer.class, sourceUri);
        Integer version2 = jdbcTemplate.queryForObject(
            "SELECT version FROM source_documents WHERE source_uri = ?", Integer.class, sourceUri);

        // Row counts and version must be unchanged
        assertThat(docCount2).isEqualTo(1);
        assertThat(chunkCount2).isEqualTo(2);
        assertThat(version2).isEqualTo(1);
    }

    @Test
    void contentChange_oldChunksReplacedVersionIncremented() {
        String sourceUri = "file://test-content-change.md";
        String originalHash = "1111111111111111111111111111111111111111111111111111111111111111";
        String updatedHash = "2222222222222222222222222222222222222222222222222222222222222222";
        int pipelineVersion = 1;

        List<ChunkWithEmbedding> originalChunks = List.of(
            new ChunkWithEmbedding(
                new Document("Original content about hiring", Map.of("source", "test-content-change.md", "section", "Hiring")),
                DUMMY_EMBEDDING
            )
        );

        List<ChunkWithEmbedding> updatedChunks = List.of(
            new ChunkWithEmbedding(
                new Document("Updated content about hiring v2", Map.of("source", "test-content-change.md", "section", "Hiring")),
                DUMMY_EMBEDDING
            ),
            new ChunkWithEmbedding(
                new Document("New section about onboarding", Map.of("source", "test-content-change.md", "section", "Onboarding")),
                DUMMY_EMBEDDING
            )
        );

        // First ingest
        chunkIndexer.index(sourceUri, originalHash, pipelineVersion, "COMPANY_POLICY", null, "test-content-change.md", originalChunks);

        Integer version1 = jdbcTemplate.queryForObject(
            "SELECT version FROM source_documents WHERE source_uri = ?", Integer.class, sourceUri);
        assertThat(version1).isEqualTo(1);

        // Verify original chunk content
        List<String> chunks1 = jdbcTemplate.queryForList(
            "SELECT dc.content FROM document_chunks dc JOIN source_documents sd ON dc.source_document_id = sd.id WHERE sd.source_uri = ?",
            String.class, sourceUri);
        assertThat(chunks1).hasSize(1);
        assertThat(chunks1.get(0)).contains("Original content");

        // Second ingest — different content hash
        chunkIndexer.index(sourceUri, updatedHash, pipelineVersion, "COMPANY_POLICY", null, "test-content-change.md", updatedChunks);

        Integer version2 = jdbcTemplate.queryForObject(
            "SELECT version FROM source_documents WHERE source_uri = ?", Integer.class, sourceUri);
        assertThat(version2).isEqualTo(2);

        // Old chunks should be gone, replaced by new ones
        List<String> chunks2 = jdbcTemplate.queryForList(
            "SELECT dc.content FROM document_chunks dc JOIN source_documents sd ON dc.source_document_id = sd.id WHERE sd.source_uri = ? ORDER BY dc.content",
            String.class, sourceUri);
        assertThat(chunks2).hasSize(2);
        assertThat(chunks2).noneMatch(c -> c.contains("Original content"));
        assertThat(chunks2).anyMatch(c -> c.contains("Updated content"));
        assertThat(chunks2).anyMatch(c -> c.contains("New section about onboarding"));
    }

    @Test
    void pipelineVersionChange_forcesReIngestion() {
        String sourceUri = "file://test-pipeline-change.md";
        String contentHash = "3333333333333333333333333333333333333333333333333333333333333333";

        List<ChunkWithEmbedding> chunksV1 = List.of(
            new ChunkWithEmbedding(
                new Document("Content v1 embedding", Map.of("source", "test-pipeline-change.md", "section", "Main")),
                DUMMY_EMBEDDING
            )
        );

        List<ChunkWithEmbedding> chunksV2 = List.of(
            new ChunkWithEmbedding(
                new Document("Content v2 embedding", Map.of("source", "test-pipeline-change.md", "section", "Main")),
                DUMMY_EMBEDDING
            )
        );

        // Ingest with pipeline version 1
        chunkIndexer.index(sourceUri, contentHash, 1, "COMPLIANCE", null, "test-pipeline-change.md", chunksV1);

        // Ingest same content hash but pipeline version 2 (chunker/embedding model changed)
        chunkIndexer.index(sourceUri, contentHash, 2, "COMPLIANCE", null, "test-pipeline-change.md", chunksV2);

        Integer version = jdbcTemplate.queryForObject(
            "SELECT version FROM source_documents WHERE source_uri = ?", Integer.class, sourceUri);
        assertThat(version).isEqualTo(2);

        // Chunks should reflect the v2 embedding
        List<String> chunks = jdbcTemplate.queryForList(
            "SELECT dc.content FROM document_chunks dc JOIN source_documents sd ON dc.source_document_id = sd.id WHERE sd.source_uri = ?",
            String.class, sourceUri);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("Content v2 embedding");
    }
}
