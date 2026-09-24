CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. No dependencies
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('RECRUITER','REVIEWER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE candidates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    raw_profile_json JSONB NOT NULL,
    role_applied VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE source_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_uri TEXT UNIQUE NOT NULL,
    content_hash CHAR(64) NOT NULL,
    pipeline_version INT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. Depends on source_documents
CREATE TABLE document_chunks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_document_id UUID NOT NULL REFERENCES source_documents(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    doc_category VARCHAR(32) NOT NULL,
    role_id VARCHAR(32),
    source_file_name TEXT NOT NULL,
    section_page VARCHAR(64) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    pipeline_version INT NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata JSONB NOT NULL,
    embedding VECTOR(1536) NOT NULL,
    search_vector TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', content)) STORED
);
CREATE INDEX idx_chunks_fts  ON document_chunks USING gin (search_vector);
CREATE INDEX idx_chunks_meta ON document_chunks USING gin (metadata);
CREATE INDEX idx_chunks_src  ON document_chunks (source_document_id);
CREATE INDEX idx_chunks_cat  ON document_chunks (doc_category);


-- 3. Depends on candidates, users
CREATE TABLE agent_runs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    candidate_id UUID NOT NULL REFERENCES candidates(id),
    submitted_by UUID REFERENCES users(id),
    current_state VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    outcome VARCHAR(32),
    last_heartbeat_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    agent_deadline_ms BIGINT NOT NULL DEFAULT 600000,
    agent_elapsed_ms BIGINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0
);

-- 4. Depends on agent_runs
CREATE TABLE agent_steps (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    run_id UUID NOT NULL REFERENCES agent_runs(id),
    agent_name VARCHAR(64) NOT NULL,
    input_json JSONB NOT NULL,
    output_json JSONB,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    status VARCHAR(16) NOT NULL,
    error TEXT
);

CREATE TABLE review_queue (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    run_id UUID NOT NULL UNIQUE REFERENCES agent_runs(id),
    decision_draft JSONB NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED','EDITED','EXECUTED')),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewer_id UUID REFERENCES users(id),
    decided_at TIMESTAMPTZ,
    executed_at TIMESTAMPTZ,
    edited_payload JSONB,
    reject_reason TEXT,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE decision_audit (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    review_queue_id UUID NOT NULL REFERENCES review_queue(id),
    reviewer_id UUID NOT NULL REFERENCES users(id),
    action VARCHAR(16) NOT NULL CHECK(action IN ('APPROVED','REJECTED','EDITED')),
    before_payload JSONB,
    after_payload JSONB,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE candidate_screening_decisions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    run_id UUID UNIQUE NOT NULL,
    candidate_id UUID NOT NULL REFERENCES candidates(id),
    decision VARCHAR(16) NOT NULL,
    rationale TEXT NOT NULL,
    citations_json JSONB NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE token_usage (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    run_id UUID,
    source VARCHAR(16) NOT NULL CHECK (source IN ('ASK','INGESTION','SCREENING')),
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('CHAT','EMBEDDING')),
    agent_name VARCHAR(64),
    model VARCHAR(64) NOT NULL,
    prompt_tokens INT NOT NULL,
    completion_tokens INT NOT NULL,
    estimated_cost_usd NUMERIC(10,6) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
