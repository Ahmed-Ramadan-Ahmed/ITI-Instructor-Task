package org.hrcopilot.api;

import java.util.Map;
import java.util.UUID;
import org.hrcopilot.service.RunTraceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/runs")
@RequiredArgsConstructor
public class RunTraceController {
    private final RunTraceService traces;

    @GetMapping("/{id}/trace")
    @PreAuthorize("hasAnyRole('RECRUITER','REVIEWER')")
    public Map<String, Object> trace(@PathVariable UUID id) {
        return traces.getTrace(id);
    }
}
