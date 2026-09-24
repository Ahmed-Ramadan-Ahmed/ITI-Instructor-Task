package org.hrcopilot.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import org.hrcopilot.persistence.entity.DocumentChunkEntity;
import org.hrcopilot.persistence.entity.SourceDocumentEntity;
import org.hrcopilot.persistence.repository.DocumentChunkRepository;
import org.hrcopilot.persistence.repository.SourceDocumentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.hrcopilot.ingestion.ChunkEmbedder.ChunkWithEmbedding;

/**
 * Replaces a document's indexed chunks atomically when its content changes.
 */
@Component
@RequiredArgsConstructor
public class ChunkIndexer {

    private final ObjectMapper objectMapper;
    private final SourceDocumentRepository sourceDocuments;
    private final DocumentChunkRepository documentChunks;

    @Transactional
    public void index(String sourceUri, String contentHash, int pipelineVersion, 
                      String docCategory, String roleId, String sourceFileName,
                      List<ChunkWithEmbedding> chunks) {
        
        SourceDocumentEntity source = sourceDocuments.findBySourceUri(sourceUri)
                .orElseGet(() -> sourceDocuments.save(new SourceDocumentEntity(sourceUri, contentHash, pipelineVersion)));

        boolean unchanged = source.getContentHash().equals(contentHash)
                && source.getPipelineVersion() == pipelineVersion;

        if (unchanged && documentChunks.countBySourceDocument_Id(source.getId()) > 0) return;

        if (!unchanged) source.updateContent(contentHash, pipelineVersion);

        documentChunks.deleteBySourceDocument_Id(source.getId());
        var entities = new ArrayList<DocumentChunkEntity>(chunks.size());

        for (ChunkWithEmbedding chunk : chunks) {

            String content = chunk.document().getText();
            String chunkContentHash = computeChunkHash(content);

            JsonNode metadata = readMetadata(chunk.document());

            entities.add(new DocumentChunkEntity(source, content, docCategory, roleId, sourceFileName,
                    deriveSectionPage(chunk.document()), chunkContentHash, pipelineVersion,
                    metadata, chunk.embedding()));
        }

        documentChunks.saveAll(entities);
    }

    private JsonNode readMetadata(org.springframework.ai.document.Document document) {
        try {
            return objectMapper.valueToTree(document.getMetadata());
        } catch (IllegalArgumentException exception) {
            return objectMapper.createObjectNode();
        }
    }

    private String deriveSectionPage(org.springframework.ai.document.Document doc) {
        // Prefer "section" metadata (set by StructuralChunker), fall back to "style" or "Page 1"
        Object section = doc.getMetadata().get("section");

        if (section != null && !section.toString().isBlank()) {
            return section.toString();
        }

        for (String key : List.of("page_number", "pageNumber", "page")) {
            Object page = doc.getMetadata().get(key);
            if (page != null) return "Page " + page;
        }

        return doc.getMetadata().getOrDefault("style", "Page 1").toString();
    }

    private String computeChunkHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
