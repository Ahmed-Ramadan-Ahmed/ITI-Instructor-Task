package org.hrcopilot.ingestion;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Extracts Markdown documents preserving the full text content.
 * Header-aware splitting is handled downstream by {@link StructuralChunker}.
 */
@Component
public class MarkdownExtractor implements DocumentExtractor {

    @Override
    public List<Document> extract(InputStream inputStream, String fileName) {
        try {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            // Return the full content as a single Document.
            // StructuralChunker will split it by # and ## headings.
            return List.of(new Document(content, Map.of("source", fileName)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to read Markdown file: " + fileName, e);
        }
    }

    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".md");
    }
}
