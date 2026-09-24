package org.hrcopilot.ingestion;

import org.springframework.ai.document.Document;
import org.hrcopilot.observability.TokenUsageService;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.hrcopilot.persistence.repository.SourceDocumentRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class IngestionPipeline implements CommandLineRunner {

    private final ExtractorFactory extractorFactory;
    private final DocumentCleaner cleaner;
    private final StructuralChunker structuralChunker;
    private final TokenCapSplitter tokenCapSplitter;
    private final ChunkEmbedder embedder;
    private final ChunkIndexer indexer;
    private final SourceDocumentRepository sourceDocuments;
    private final TokenUsageService usage;
    @Value("${app.corpus-path:./corpus}") private String corpusPath;
    
    private static final int PIPELINE_VERSION = 1;

    @Override
    public void run(String... args) throws Exception {
        java.nio.file.Path root = java.nio.file.Path.of(corpusPath);
        if (!java.nio.file.Files.isDirectory(root)) return;

        try (var files = java.nio.file.Files.walk(root)) {

            for (var path : files.filter(java.nio.file.Files::isRegularFile).toList()) {

                String name = path.getFileName().toString();
                if (!name.matches("(?i).*\\.(pdf|docx|md)$")) continue;

                String relative = root.relativize(path).toString().replace("\\", "/");
                String hash = computeHash(java.nio.file.Files.readAllBytes(path));
                var existing = sourceDocuments.findBySourceUri("file://" + relative);

                if (existing.isEmpty() || !existing.get().getContentHash().equals(hash)) {

                    String category = categoryFor(relative);
                    try (var in = java.nio.file.Files.newInputStream(path)) {
                        ingest(in, relative, category, null);
                    }
                }
            }
        }
    }

    private String categoryFor(String path) {
        String lower = path.toLowerCase();
        if (lower.contains("rubric")) return "RUBRIC";
        if (lower.contains("compliance")) return "COMPLIANCE";
        if (lower.contains("governance")) return "AI_GOVERNANCE";
        if (lower.contains("policy")) return "COMPANY_POLICY";
        if (lower.contains("job") || lower.contains("jd")) return "JOB_DESCRIPTION";
        return "REFERENCE";
    }

    public void ingest(InputStream inputStream, String fileName, String docCategory, String roleId) throws Exception {
        processContent(inputStream.readAllBytes(), fileName, docCategory, roleId);
    }

    private void processContent(byte[] bytes, String fileName, String docCategory, String roleId) throws Exception {
        String contentHash = computeHash(bytes);
        String sourceUri = "file://" + fileName.replace("\\", "/");
        var known = sourceDocuments.findBySourceUri(sourceUri);
        if (known.isPresent() && known.get().getContentHash().equals(contentHash)) return;
        
        // Use a ByteArrayInputStream so we don't consume the original stream
        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bytes);
        
        // 1. Extract
        DocumentExtractor extractor = extractorFactory.getExtractor(fileName);
        List<Document> rawDocs = extractor.extract(bais, fileName);
        
        // 2. Clean
        List<Document> cleanDocs = cleaner.clean(rawDocs);
        
        // 3. Chunk & Split
        List<Document> structuralChunks = structuralChunker.chunk(cleanDocs);
        List<Document> finalChunks = tokenCapSplitter.split(structuralChunks);
        
        // 4. Embed
        List<ChunkEmbedder.ChunkWithEmbedding> embeddedChunks = embedder.embed(finalChunks);
        usage.record("INGESTION", "EMBEDDING", "gemini-embedding-001",
            finalChunks.stream().mapToInt(d -> TokenUsageService.roughTokens(d.getText())).sum(), 0, "IngestionPipeline");
        
        // 5. Index
        indexer.index(sourceUri, contentHash, PIPELINE_VERSION, docCategory, roleId, fileName, embeddedChunks);
    }
    
    private String computeHash(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content);
        return HexFormat.of().formatHex(hash);
    }
}
