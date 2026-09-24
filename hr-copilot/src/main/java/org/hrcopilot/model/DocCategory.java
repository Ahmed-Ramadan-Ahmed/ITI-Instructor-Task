package org.hrcopilot.model;

/**
 * Document categories used to scope retrieval per agent.
 * Each agent gets a fixed Set<DocCategory> — intersected, never widened.
 */
public enum DocCategory {
    JOB_DESCRIPTION,
    RUBRIC,
    COMPLIANCE,
    COMPANY_POLICY,
    AI_GOVERNANCE,
    REFERENCE
}
