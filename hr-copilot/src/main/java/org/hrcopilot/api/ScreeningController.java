package org.hrcopilot.api;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.hrcopilot.agents.AgentRecords.CandidateInput;
import org.hrcopilot.agents.LinearScreeningOrchestrator;
import org.hrcopilot.service.CandidateDocumentExtractor;
import org.hrcopilot.service.ReviewService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ScreeningController {

    private final LinearScreeningOrchestrator orchestrator;
    private final ReviewService reviews;
    private final CandidateDocumentExtractor documentExtractor;

    @PostMapping("/screenings")
    @PreAuthorize("hasRole('RECRUITER')")
    public Map<String, String> submit(@Valid @RequestBody CandidateRequest request,
                                     Authentication authentication, HttpServletResponse response) {
        return submitCandidate(
                request.name(),
                request.roleApplied(),
                request.profileJson(),
                authentication,
                response
        );
    }

    @PostMapping(value = "/screenings/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('RECRUITER')")
    public Map<String, String> submitDocument(@RequestParam @NotBlank String name,
            @RequestParam @NotBlank String roleApplied, @RequestPart("profileFile") MultipartFile profileFile,
            Authentication authentication, HttpServletResponse response) {

        String profile = documentExtractor.toProfileJson(profileFile);
        return submitCandidate(
                name,
                roleApplied,
                profile,
                authentication,
                response);
    }

    @PostMapping(value = "/screenings/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('RECRUITER')")
    public CandidateDocumentExtractor.ExtractedProfile extractDocument(
            @RequestPart("profileFile") MultipartFile profileFile) {
        return documentExtractor.extractProfile(profileFile);
    }

    @GetMapping("/reviews")
    @PreAuthorize("hasRole('REVIEWER')")
    public java.util.List<Map<String, Object>> pendingReviews() {
        return reviews.pendingReviews();
    }

    @PostMapping("/reviews/{id}/approve")
    @PreAuthorize("hasRole('REVIEWER')")
    public Map<String, String> approve(@PathVariable UUID id, Authentication authentication) {
        reviews.decide(id, authentication.getName(), "APPROVED", null, null);
        return Map.of("status", "APPROVED");
    }

    @PostMapping("/reviews/{id}/reject")
    @PreAuthorize("hasRole('REVIEWER')")
    public Map<String, String> reject(@PathVariable UUID id, @RequestBody(required = false) ReasonRequest request,
                                      Authentication authentication) {
        reviews.decide(id, authentication.getName(), "REJECTED",
                request == null ? "" : request.reason(), null);
        return Map.of("status", "REJECTED");
    }

    @PostMapping("/reviews/{id}/edit-approve")
    @PreAuthorize("hasRole('REVIEWER')")
    public Map<String, String> editAndApprove(@PathVariable UUID id, @Valid @RequestBody EditRequest request,
                                              Authentication authentication) {
        reviews.decide(id, authentication.getName(), "EDITED", request.reason(), request.editedPayload());
        return Map.of("status", "EDITED");
    }

    @PostMapping("/reviews/{id}/execute")
    @PreAuthorize("hasRole('REVIEWER')")
    public Map<String, String> execute(@PathVariable UUID id) {
        int inserted = reviews.execute(id);
        return Map.of("status", inserted == 1 ? "EXECUTED" : "ALREADY_RECORDED");
    }

    private Map<String, String> submitCandidate(String name, String roleApplied, String profileJson,
                                               Authentication authentication, HttpServletResponse response) {
        UUID runId = orchestrator.submit(new CandidateInput(name, roleApplied, profileJson), authentication.getName());
        response.setHeader("X-Run-Id", runId.toString());
        return Map.of("runId", runId.toString(), "state", "SUBMITTED");
    }

    public record CandidateRequest(@NotBlank String name, @NotBlank String roleApplied,
                                   @NotBlank String profileJson) { }
    public record EditRequest(@NotBlank String editedPayload, String reason) { }
    public record ReasonRequest(String reason) { }
}
