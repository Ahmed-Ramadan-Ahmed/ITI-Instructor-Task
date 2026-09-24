package org.hrcopilot.ingestion;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DocxExtractor implements DocumentExtractor {

    @Override
    public List<Document> extract(InputStream inputStream, String fileName) {
        List<Document> documents = new ArrayList<>();

        try (XWPFDocument docx = new XWPFDocument(inputStream)) {

            for (XWPFParagraph paragraph : docx.getParagraphs()) {

                String text = paragraph.getText().trim();

                if (!text.isEmpty()) {
                    String style = paragraph.getStyleID();

                    documents.add(new Document(text, Map.of(
                        "source", fileName,
                        "style", style != null ? style : "Normal"
                    )));
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse DOCX: " + fileName, e);
        }

        return documents;
    }

    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".docx");
    }
}
