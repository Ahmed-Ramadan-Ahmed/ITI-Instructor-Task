package org.hrcopilot.ingestion;

import org.springframework.ai.document.Document;
import java.util.List;
import java.io.InputStream;

public interface DocumentExtractor {
    List<Document> extract(InputStream inputStream, String fileName);
    boolean supports(String fileName);
}
