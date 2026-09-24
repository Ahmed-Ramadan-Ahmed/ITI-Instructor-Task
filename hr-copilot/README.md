# HR Screening Copilot

A small Spring Boot / PostgreSQL prototype for grounded HR policy Q&A and human-reviewed screening drafts. It is an internal demo, uses synthetic candidate and policy material, and does not make final hiring decisions.

## Retrieval design

The synthetic corpus has 15 documents and 63 pages: six five-page PDFs, six five-page DOCX files, and three one-page Markdown files. Documents now contain distinct, practical guidance rather than repeated generic filler: role-specific requirements and rubric anchors, evidence-note examples, consistent interview practices, candidate-data safeguards, reviewer actions, and AI oversight. The materials are explicitly synthetic and avoid asserting jurisdiction-specific legal rules. Structural chunking follows PDF pages and DOCX/Markdown headings because hiring policies, role descriptions, and rubrics are organized into named sections reviewers need to cite. The 500-token cap and 50-token overlap keep chunks focused while retaining nearby context. Every chunk stores its source document, section/page, ingestion version, category metadata, and embedding.

Search combines cosine dense rank and PostgreSQL English full-text rank using reciprocal rank fusion with `k=60`; this lets semantic matches and exact HR terms contribute ranks without requiring their raw scores to share a scale. The additional enhancement is category metadata filtering: each specialist has a fixed allowlist so role research and compliance checks search only relevant document types. An answer is refused when none of the top eight fused chunks has cosine similarity of at least 0.65 (equivalently distance greater than 0.35). Citations identify the source document, section/page, and chunk ID.

## Agent workflow and approval

The named orchestration pattern is a **linear persisted state machine**: it is easy to inspect at MVP scale, and every stage writes a named state before moving on. PolicyResearchAgent retrieves only job descriptions/rubrics and deterministically passes through relevant cited chunks; ComplianceGuardAgent retrieves only compliance/company policy/AI governance; CandidateEvaluatorAgent scores job-related evidence; RecommendationAgent drafts an outcome and carries compliance flags forward. Typed records connect the stages. Each stage has a 30-second timeout and the workflow caps its stage count at six. Structured model responses tolerate a surrounding Markdown fence or short preamble but are strictly parsed as JSON objects. A failed stage queues a fixed `HOLD` draft that explicitly says no recommendation was produced and requires manual review; it does not substitute a general policy answer as candidate rationale.

Recruiters submit a candidate name, role, and PDF/Markdown/DOC/DOCX resume. The profile analyzer displays the first clearly stated education line and explicit years of experience; when duration is unclear it displays 0 years with a clearly labeled freshman assumption. CandidateEvaluatorAgent scores four role-specific criteria from 0 to 5; the match indicator is the rounded mean converted to 0–100. The default triage threshold is 70% (`SCREENING_MATCH_THRESHOLD_PERCENT` can configure it). The threshold is informational: below-threshold candidates receive a HOLD draft and still go to a reviewer. The system never auto-rejects; reviewers decide every outcome.

Reviewers can approve, reject, or edit-and-approve. The review card displays candidate name, role, education, experience, match score, threshold explanation, criterion scores, evidence summary, and supporting citations. All outcomes record reviewer, time, and edit before/after values. Only a separate reviewer-only execute endpoint writes a screening decision; the write validates the draft schema, run/candidate identity, and approval status in a transaction.

| Agent | Restricted capability | Done condition |
|---|---|---|
| PolicyResearchAgent | `search_corpus` for JOB_DESCRIPTION/RUBRIC only | Typed requirements summary with retrieved evidence |
| ComplianceGuardAgent | `search_corpus` for COMPLIANCE/COMPANY_POLICY/AI_GOVERNANCE only | One typed compliance verdict with flags/citations |
| CandidateEvaluatorAgent | No corpus write tools | Typed job-related score map and evidence summary |
| RecommendationAgent | `draft_decision` validation only | Typed decision draft carries citations and all compliance flags |
| DecisionExecutorAgent | `submit_screening_decision` only | Approved decision persisted, or approval check fails |

The four tools are `search_corpus` (fixed category scopes), `get_chunk` (read-only evidence lookup), `draft_decision` (typed draft validation), and `submit_screening_decision` (the only consequential write). The decision write validates the payload and uses an atomic status transition that succeeds only for an approved or edited review.

## Setup

Requirements: Java 21, Maven, Docker, and a Gemini API key. Configure `GEMINI_API_KEY`; the chat model defaults to `gemini-3.5-flash-lite` and can be overridden with `GEMINI_MODEL`. For a non-demo deployment, also set `APP_JWT_SECRET`, `APP_SEED_RECRUITER_PASSWORD`, and `APP_SEED_REVIEWER_PASSWORD` to private values before first startup. The local demo account defaults are `recruiter` / `RecruiterDemo!23` and `reviewer` / `ReviewerDemo!23`; they are for local use only. The seeded accounts are not updated after the initial insert.

1. Start PostgreSQL with `docker compose up -d` (Docker Desktop must be running).
2. In PowerShell, set `GEMINI_API_KEY`; optionally set `GEMINI_MODEL` (defaults to `gemini-3.5-flash-lite`). For deployments beyond the local demo, also set `APP_JWT_SECRET`, `APP_SEED_RECRUITER_PASSWORD`, and `APP_SEED_REVIEWER_PASSWORD` before first startup.
3. Start the API with `mvn spring-boot:run`. Flyway creates the schema and the boot runner ingests changed files under `corpus/`. Confirm startup with `Invoke-RestMethod http://localhost:8080/actuator/health`.
4. Run the automated tests with `mvn test`.
5. With the normal app stopped, run the live golden set with `mvn '-Dspring-boot.run.main-class=org.hrcopilot.evaluation.GoldenSetEvaluator' spring-boot:run`. It writes measured results to `eval-report.md` and needs both Gemini API access and the configured PostgreSQL database.

## Web UI

With the API running, open `http://localhost:8080/`. Sign in with either the recruiter or reviewer account configured above. Recruiters can ask grounded questions, submit a screening draft, and inspect its run trace. Reviewers can inspect pending drafts, approve and execute, edit then approve and execute, or reject them. The browser keeps the JWT in memory only; signing out or refreshing the tab clears the session. The API remains the source of role enforcement and the approval gate.

## Numbered demo (PowerShell)

Run these commands from PowerShell after startup. The demo credentials are local-only defaults; replace them if the seed passwords were overridden.

```powershell
$base = 'http://localhost:8080'

# 1. Authenticate as the recruiter.
$recruiterToken = (Invoke-RestMethod -Method Post -Uri "$base/api/auth/token" `
  -ContentType 'application/json' `
  -Body (@{ username = 'recruiter'; password = 'RecruiterDemo!23' } | ConvertTo-Json)).accessToken
$recruiterHeaders = @{ Authorization = "Bearer $recruiterToken" }

# 2. Ask a grounded question; inspect citations and the correlation ID.
$answer = Invoke-RestMethod -Method Post -Uri "$base/api/ask" `
  -Headers $recruiterHeaders -ContentType 'application/json' `
  -Body (@{ question = 'What evidence should a software engineering reviewer score?' } | ConvertTo-Json) `
  -ResponseHeadersVariable askHeaders
$answer | ConvertTo-Json -Depth 8
$askHeaders['X-Run-Id']

# 3. Submit a synthetic candidate. profileJson is a JSON string by API design.
$profile = '{"workSample":"Built a Java REST service and added automated tests","experience":"Two years maintaining backend services"}'
$screening = Invoke-RestMethod -Method Post -Uri "$base/api/screenings" `
  -Headers $recruiterHeaders -ContentType 'application/json' `
  -Body (@{ name = 'Demo Candidate'; roleApplied = 'Software Engineer'; profileJson = $profile } | ConvertTo-Json)
$screening

The recruiter UI also accepts PDF, legacy Word DOC, and DOCX candidate profiles. Choose **Upload PDF or Word document**, select a file (up to 10 MB), review the extracted text, then submit the screening. Text-based PDFs and Word documents are supported; image-only scanned PDFs require OCR and are rejected with a clear message. The extraction endpoint is recruiter-only: `POST /api/screenings/extract` as multipart form field `profileFile`. The direct upload route `POST /api/screenings/upload` accepts `name`, `roleApplied`, and `profileFile` and submits the extracted text into the same screening pipeline. JSON submission remains supported.

# 4. Authenticate as the reviewer and wait for this run to enter the review queue.
$reviewerToken = (Invoke-RestMethod -Method Post -Uri "$base/api/auth/token" `
  -ContentType 'application/json' `
  -Body (@{ username = 'reviewer'; password = 'ReviewerDemo!23' } | ConvertTo-Json)).accessToken
$reviewerHeaders = @{ Authorization = "Bearer $reviewerToken" }
$review = $null
for ($attempt = 0; $attempt -lt 60 -and -not $review; $attempt++) {
  $review = Invoke-RestMethod -Uri "$base/api/reviews" -Headers $reviewerHeaders |
    Where-Object { $_.run_id -eq $screening.runId } | Select-Object -First 1
  if (-not $review) { Start-Sleep -Seconds 2 }
}
if (-not $review) { throw "No review item appeared for run $($screening.runId); inspect its trace." }
$review | ConvertTo-Json -Depth 8

# 5. Human approval is required before execution. Choose one review outcome.
Invoke-RestMethod -Method Post -Uri "$base/api/reviews/$($review.id)/approve" -Headers $reviewerHeaders
Invoke-RestMethod -Method Post -Uri "$base/api/reviews/$($review.id)/execute" -Headers $reviewerHeaders

# 6. Inspect persisted agent steps, token usage, citations, and review history.
Invoke-RestMethod -Uri "$base/api/runs/$($screening.runId)/trace" -Headers $recruiterHeaders |
  ConvertTo-Json -Depth 12
```

For the reject branch, call `POST /api/reviews/{id}/reject` with `{"reason":"..."}` instead of approving; rejected items cannot be executed. For edit-and-approve, call `POST /api/reviews/{id}/edit-approve` with `{"editedPayload":"<JSON decision draft>","reason":"..."}`. The reviewer-only audit records the actor, timestamp, action, and before/after payloads.

## Security and observability

The only roles are RECRUITER and REVIEWER. JWT authentication is stateless; method security enforces role-specific routes. All database values use JDBC bind parameters. Retrieved text is explicitly marked as untrusted data in each model prompt. Actuator exposes health/readiness only.

Token/cost rows are stored in `token_usage`. Query a run with `SELECT source,kind,agent_name,model,prompt_tokens,completion_tokens,estimated_cost_usd FROM token_usage WHERE run_id = '<run UUID>' ORDER BY created_at;`. Chat token counts come from provider response metadata; embedding usage is estimated from input text length because the embedding response does not expose usage consistently through the configured Spring AI interface. Rates are configured in `application.yml`.

## Evaluation

`eval/golden-set.yaml` contains 15 cases, including an out-of-corpus question, an ambiguous question, an indirect prompt injection stored in the corpus, and a direct prompt injection. Run the evaluator only with the application database and Gemini access configured. It prints and writes retrieval hit-rate, judge-rated groundedness, refusal correctness for items 1–12, ambiguous clarification for item 13, and injection resistance for items 14–15 separately. The checked-in `eval-report.md` contains the latest live measurements and an interpretation, including the clarification failure; rerun the evaluator after meaningful prompt, threshold, corpus, or model changes.
