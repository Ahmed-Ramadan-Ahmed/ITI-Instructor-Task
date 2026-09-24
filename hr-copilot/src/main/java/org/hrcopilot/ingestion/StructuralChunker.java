package org.hrcopilot.ingestion;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Heading-based structural chunker.
 * <p>
 * DOCX: groups paragraphs by Heading1/Heading2 styles (via POI paragraph styleID).
 * Markdown: splits on # and ## headers.
 * PDF: passes through as-is (page-level chunks — accepted degradation, documented in README).
 * <p>
 * Oversized sections are handled downstream by {@link TokenCapSplitter}.
 */
@Component
public class StructuralChunker {

    /**
     * Chunk documents by structural headings.
     * The strategy depends on the source format, detected via metadata.
     */
    public List<Document> chunk(List<Document> documents) {
        if (documents.isEmpty()) {
            return documents;
        }

        // Detect format from first document's metadata
        Object source = documents.get(0).getMetadata().get("source");
        String sourceName = source != null ? source.toString().toLowerCase() : "";

        if (sourceName.endsWith(".docx")) {
            return chunkDocx(documents);
        } else if (sourceName.endsWith(".md")) {
            return chunkMarkdown(documents);
        } else {
            // PDF or unknown: pass through (page-level chunks from extractor)
            return documents;
        }
    }

    /**
     * DOCX: merge paragraphs under the same heading.
     * DocxExtractor emits one Document per paragraph with metadata "style" = Heading1/Heading2/Normal.
     */
    private List<Document> chunkDocx(List<Document> paragraphs) {
        List<Document> chunks = new ArrayList<>();
        StringBuilder currentSection = new StringBuilder();
        String currentHeading = "Introduction";
        Map<String, Object> currentMeta = new HashMap<>();

        for (Document para : paragraphs) {

            String style = String.valueOf(para.getMetadata().getOrDefault("style", "Normal"));
            boolean isHeading = style.startsWith("Heading") || style.startsWith("heading");

            if (isHeading && currentSection.length() > 0) {
                // Flush previous section
                currentMeta.put("section", currentHeading);
                chunks.add(new Document(currentSection.toString().trim(), new HashMap<>(currentMeta)));
                currentSection.setLength(0);
            }

            if (isHeading) {
                currentHeading = para.getText().trim();
                // Carry over source metadata
                currentMeta = new HashMap<>(para.getMetadata());
            } else {

                if (currentMeta.isEmpty()) {
                    currentMeta = new HashMap<>(para.getMetadata());
                }

                currentSection.append(para.getText().trim()).append("\n\n");
            }
        }

        // Flush last section
        if (currentSection.length() > 0) {
            currentMeta.put("section", currentHeading);
            chunks.add(new Document(currentSection.toString().trim(), new HashMap<>(currentMeta)));
        }

        return chunks.isEmpty() ? paragraphs : chunks;
    }

    /**
     * Markdown: split on # and ## headers.
     * MarkdownExtractor may return a single Document with the full file content,
     * or multiple documents split by headers. Handle both.
     */
    private List<Document> chunkMarkdown(List<Document> documents) {
        List<Document> chunks = new ArrayList<>();

        for (Document doc : documents) {
            String content = doc.getText();
            Map<String, Object> baseMeta = doc.getMetadata();

            // Split on markdown headings (# or ##)
            String[] lines = content.split("\n");
            StringBuilder currentSection = new StringBuilder();
            String currentHeading = "Introduction";

            for (String line : lines) {
                String trimmed = line.trim();

                if (trimmed.startsWith("# ") || trimmed.startsWith("## ")) {
                    // Flush previous section
                    if (currentSection.length() > 0) {
                        Map<String, Object> meta = new HashMap<>(baseMeta);
                        meta.put("section", currentHeading);
                        chunks.add(new Document(currentSection.toString().trim(), meta));
                        currentSection.setLength(0);
                    }
                    currentHeading = trimmed.replaceFirst("^#+\\s*", "");
                } else {
                    currentSection.append(line).append("\n");
                }
            }

            // Flush last section
            if (currentSection.length() > 0) {
                Map<String, Object> meta = new HashMap<>(baseMeta);
                meta.put("section", currentHeading);
                chunks.add(new Document(currentSection.toString().trim(), meta));
            }
        }

        return chunks.isEmpty() ? documents : chunks;
    }
}
