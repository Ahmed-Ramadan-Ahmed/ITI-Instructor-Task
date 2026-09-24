package org.hrcopilot;

import com.pgvector.PGvector;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "spring.ai.google.genai.api-key=AQ.Ab8RN6JIHVaqVWp3M3KxqEHQHyCA0xgD5uOKhHldQkwXPpBLuw"
})
public class Phase0VerificationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("hr_copilot")
            .withUsername("hr_copilot")
            .withPassword("hr_copilot");

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testEmbeddingDimensionsAndDatabaseRoundTrip() {
        // 1. Verify 1536-dim truncation via application.yml config
        float[] embedding = embeddingModel.embed("Test document content for HR Copilot phase 0");
        
        System.out.println("Returned embedding dimensions: " + embedding.length);
        assertThat(embedding).hasSize(1536);

        // 2. Setup mock source document
        UUID sourceDocId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO source_documents (id, source_uri, content_hash, pipeline_version) 
            VALUES (?, ?, ?, ?)
            """, 
            sourceDocId, "file://dummy.txt", "abc123hash", 1);

        // 3. Insert into document_chunks
        UUID chunkId = UUID.randomUUID();
        PGvector pgVector = new PGvector(embedding);
        
        jdbcTemplate.update("""
            INSERT INTO document_chunks 
            (id, source_document_id, content, doc_category, source_file_name, section_page, content_hash, pipeline_version, metadata, embedding)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            """,
            chunkId, sourceDocId, "Test document content for HR Copilot phase 0", "COMPANY_POLICY", 
            "dummy.txt", "Page 1", "def456hash", 1, "{}", pgVector);

        // 4. Retrieve via hybrid query (simple RRF test)
        float[] queryEmbedding = embeddingModel.embed("HR Copilot policy");
        PGvector queryPgVector = new PGvector(queryEmbedding);

        String hybridQuery = """
            WITH semantic_search AS (
                SELECT id, embedding <=> ? AS distance,
                       RANK() OVER (ORDER BY embedding <=> ?) AS dense_rank
                FROM document_chunks
            ),
            keyword_search AS (
                SELECT id, ts_rank(search_vector, plainto_tsquery('english', ?)) AS fts_score,
                       RANK() OVER (ORDER BY ts_rank(search_vector, plainto_tsquery('english', ?)) DESC) AS kw_rank
                FROM document_chunks
            )
            SELECT 
                COALESCE(s.id, k.id) as chunk_id,
                COALESCE(1.0 / (60 + s.dense_rank), 0.0) + COALESCE(1.0 / (60 + k.kw_rank), 0.0) as rrf_score,
                s.distance as dense_distance,
                k.fts_score
            FROM semantic_search s
            FULL OUTER JOIN keyword_search k ON s.id = k.id
            ORDER BY rrf_score DESC
            LIMIT 10
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(hybridQuery, 
            queryPgVector, queryPgVector, "HR Copilot", "HR Copilot");

        assertThat(results).isNotEmpty();
        Map<String, Object> topHit = results.get(0);
        assertThat(topHit.get("chunk_id")).isEqualTo(chunkId);
        
        System.out.println("✅ Hybrid query successful.");
        System.out.println("Top hit distance: " + topHit.get("dense_distance"));
        System.out.println("Top hit RRF score: " + topHit.get("rrf_score"));
    }
}
