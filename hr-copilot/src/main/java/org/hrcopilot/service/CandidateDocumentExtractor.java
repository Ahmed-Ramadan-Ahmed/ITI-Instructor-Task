package org.hrcopilot.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Extracts textual resume content from the supported candidate document formats. */
@Service
@RequiredArgsConstructor
public class CandidateDocumentExtractor {
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_TEXT_CHARS = 50_000;
    private final ObjectMapper objectMapper;

    public ExtractedProfile extractProfile(MultipartFile file) {
        String fileName = file.getOriginalFilename() == null ? "candidate document" : file.getOriginalFilename();
        return new ExtractedProfile(fileName, extract(file));
    }

    public String toProfileJson(MultipartFile file) {
        ExtractedProfile profile = extractProfile(file);
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "sourceFile", profile.sourceFile(), "extractedText", profile.extractedText()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not encode the extracted candidate profile.", e);
        }
    }

    public record ExtractedProfile(String sourceFile, String extractedText) { }

    public String extract(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a non-empty PDF, Markdown, DOC, or DOCX file.");
        }

        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Candidate documents must be 10 MB or smaller.");
        }

        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = filename.toLowerCase(Locale.ROOT);

        try {
            byte[] bytes = file.getBytes();
            String text;
            if (ext.endsWith(".pdf")) {
                if (!startsWith(bytes, "%PDF-".getBytes())) throw new IllegalArgumentException("The selected file is not a valid PDF.");
                try (var document = Loader.loadPDF(bytes)) { text = new PDFTextStripper().getText(document); }
            } else if (ext.endsWith(".docx")) {
                if (!startsWith(bytes, new byte[]{'P','K',3,4})) {
                    throw new IllegalArgumentException("The selected file is not a valid DOCX document.");
                }
                try (var document = new XWPFDocument(new ByteArrayInputStream(bytes));
                     var extractor = new XWPFWordExtractor(document)) {
                    text = extractor.getText();
                }

            } else if (ext.endsWith(".doc")) {
                if (
                        !startsWith(
                        bytes,
                        new byte[]{(byte)0xD0,(byte)0xCF,0x11,(byte)0xE0,(byte)0xA1,(byte)0xB1,0x1A,(byte)0xE1}
                        )
                ) {
                    throw new IllegalArgumentException("The selected file is not a valid legacy DOC document.");
                }

                try (var document = new HWPFDocument(new ByteArrayInputStream(bytes));
                     var extractor = new WordExtractor(document)
                ) {
                    text = extractor.getText();
                }
            }
            else if (ext.endsWith(".md")) {
                text = new String(bytes, StandardCharsets.UTF_8);
            } else {
                throw new IllegalArgumentException("Unsupported file type. Upload PDF, Markdown (.md), DOC, or DOCX.");
            }
            text = text == null ? "" : text.replace('\u0000', ' ').trim();
            if (text.isBlank()) throw new IllegalArgumentException("No readable text was found. Scanned image-only PDFs need OCR before upload.");
            return text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read this document. Check that it is not corrupt or password-protected.", e);
        }
    }

    private static boolean startsWith(byte[] bytes, byte[] signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) if (bytes[i] != signature[i]) return false;
        return true;
    }
}
