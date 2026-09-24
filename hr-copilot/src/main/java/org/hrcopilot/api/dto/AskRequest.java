package org.hrcopilot.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/ask.
 */
public record AskRequest(
    @NotBlank(message = "Question must not be blank")
    @Size(max = 10000, message = "Question must not exceed 10,000 characters")
    String question
) {}
