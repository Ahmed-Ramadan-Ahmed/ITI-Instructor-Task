package org.hrcopilot.api;

import jakarta.validation.Valid;
import org.hrcopilot.api.dto.AskRequest;
import org.hrcopilot.api.dto.AskResponse;
import org.hrcopilot.service.AskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;

/**
 * Baseline Q&A endpoint.
 * Accepts a question, performs hybrid retrieval across the full corpus,
 * calls Gemini for a grounded answer, and returns the answer with citations.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AskController {

    private final AskService askService;

    @PostMapping("/ask")
    @PreAuthorize("hasAnyRole('RECRUITER','REVIEWER')")
    public ResponseEntity<AskResponse> ask(@Valid @RequestBody AskRequest request) {

        AskResponse response = askService.ask(request.question());
        return ResponseEntity.ok(response);
    }
}
