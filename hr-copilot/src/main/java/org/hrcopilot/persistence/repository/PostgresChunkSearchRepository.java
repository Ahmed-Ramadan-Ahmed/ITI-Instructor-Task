package org.hrcopilot.persistence.repository;

import com.pgvector.PGvector;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hrcopilot.retrieval.RetrievedChunk;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PostgresChunkSearchRepository implements ChunkSearchRepository {
    private static final String HYBRID_SEARCH_SQL = """
            WITH semantic_search AS (
                SELECT id, source_document_id, source_file_name, section_page, content,
                       embedding <=> :queryVector AS distance,
                       RANK() OVER (ORDER BY embedding <=> :queryVector) AS dense_rank
                FROM document_chunks
                WHERE doc_category IN (:categories)
                  AND (CAST(:sourceFilter AS text) IS NULL OR source_file_name = CAST(:sourceFilter AS text))
            ),
                    
            keyword_search AS (
                SELECT id,
                       ts_rank(search_vector, plainto_tsquery('english', :queryText)) AS fts_score,
                       RANK() OVER (ORDER BY ts_rank(search_vector, plainto_tsquery('english', :queryText)) DESC) AS kw_rank
                FROM document_chunks
                WHERE doc_category IN (:categories)
                  AND (CAST(:sourceFilter AS text) IS NULL OR source_file_name = CAST(:sourceFilter AS text))
                  AND search_vector @@ plainto_tsquery('english', :queryText)
            )
            
            SELECT COALESCE(s.id, k.id) AS chunk_id, s.source_document_id, s.source_file_name,
                   s.section_page, s.content,
                   COALESCE(1.0 / (60 + s.dense_rank), 0.0) + COALESCE(1.0 / (60 + k.kw_rank), 0.0) AS rrf_score,
                   s.distance AS dense_distance
            FROM semantic_search s FULL OUTER JOIN keyword_search k ON s.id = k.id
            ORDER BY rrf_score DESC LIMIT 8
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<RetrievedChunk> hybridSearch(String query, float[] queryEmbedding,
                                             Set<String> categories, String sourceFilter) {
        var parameters = new MapSqlParameterSource()
                .addValue("queryVector", new PGvector(queryEmbedding))
                .addValue("queryText", query)
                .addValue("categories", categories)
                .addValue("sourceFilter", sourceFilter);

        return jdbc.query(HYBRID_SEARCH_SQL, parameters, (rs, row) -> new RetrievedChunk(
                rs.getObject("chunk_id", UUID.class),
                rs.getObject("source_document_id", UUID.class),
                rs.getString("source_file_name"),
                rs.getString("section_page"),
                rs.getString("content"),
                rs.getDouble("rrf_score"),
                rs.getDouble("dense_distance")));
    }
}
