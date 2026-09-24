package org.hrcopilot.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;
/**
    helps you see what the app did during a screening run and diagnose problems.
    useful when a screening fails or produces an unexpected result
 */
@Component
public class RunIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String supplied = request.getHeader("X-Run-Id");
        String id;
        try {
            id = supplied == null ? UUID.randomUUID().toString() : UUID.fromString(supplied).toString();
        }
        catch (IllegalArgumentException e) {
            id = UUID.randomUUID().toString();
        }

        MDC.put("runId", id);
        response.setHeader("X-Run-Id", id);

        try {
            chain.doFilter(request, response);
        }
        finally {
            MDC.remove("runId");
        }
    }
}
