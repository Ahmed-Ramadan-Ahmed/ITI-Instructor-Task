package org.hrcopilot.ingestion;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ChunkEmbedder {

    private final EmbeddingModel embeddingModel;

    public ChunkEmbedder(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public List<ChunkWithEmbedding> embed(List<Document> chunks) {
        List<String> texts = chunks.stream().map(Document::getText).collect(Collectors.toList());
        List<float[]> embeddings = embeddingModel.embed(texts);

        List<ChunkWithEmbedding> result = new java.util.ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            result.add(new ChunkWithEmbedding(chunks.get(i), embeddings.get(i)));
        }
        return result;
    }

    public record ChunkWithEmbedding(Document document, float[] embedding) {}
}
