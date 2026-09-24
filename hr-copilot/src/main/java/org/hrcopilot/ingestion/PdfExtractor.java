package org.hrcopilot.ingestion;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class PdfExtractor implements DocumentExtractor {

    @Override
    public List<Document> extract(InputStream inputStream, String fileName) {
        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(new InputStreamResource(inputStream));
        return pdfReader.get(); // one Document per PDF page
    }

    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".pdf");
    }
}
