package org.hrcopilot.ingestion;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class DocumentCleaner {

    public List<Document> clean(List<Document> documents) {
        return documents.stream()
                .map(doc -> {
                    String cleanedContent = doc.getText()
                            .replaceAll("\\r\\n", "\n")
                            .replaceAll("\\n{3,}", "\n\n")
                            .trim();
                    return new Document(cleanedContent, doc.getMetadata());
                })
                .filter(doc -> !doc.getText().isEmpty())
                .collect(Collectors.toList());
    }
}
