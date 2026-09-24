package org.hrcopilot.ingestion;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExtractorFactory {

    private final List<DocumentExtractor> extractors;

    public ExtractorFactory(List<DocumentExtractor> extractors) {
        this.extractors = extractors;
    }

    public DocumentExtractor getExtractor(String fileName) {
        return extractors.stream()
                .filter(extractor -> extractor.supports(fileName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported file type: " + fileName));
    }
}
