-- ============================================================
-- Trust Score — Réputation financière nationale
-- ============================================================
CREATE SCHEMA IF NOT EXISTS trust_score;

CREATE TABLE trust_score.scores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id VARCHAR(100) NOT NULL,
    score_value INTEGER NOT NULL CHECK (score_value BETWEEN 0 AND 1000),
    score_level VARCHAR(20) NOT NULL, -- CRITICAL, LOW, FAIR, GOOD, EXCELLENT
    computed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    factors JSONB NOT NULL, -- détail des facteurs (explicabilité — exigence réglementaire)
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_score_level CHECK (score_level IN ('CRITICAL','LOW','FAIR','GOOD','EXCELLENT'))
);

CREATE INDEX idx_scores_citizen ON trust_score.scores(citizen_id, computed_at DESC) WHERE NOT deleted;
CREATE INDEX idx_scores_level ON trust_score.scores(score_level) WHERE NOT deleted;

-- Seule la version la plus récente est "active"
CREATE UNIQUE INDEX idx_scores_citizen_current ON trust_score.scores(citizen_id) WHERE NOT deleted AND version = 1;

CREATE TABLE trust_score.score_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(50) NOT NULL, -- CREDIT_REPAID, CREDIT_DEFAULTED, DEBT_HONORED, etc.
    event_reference VARCHAR(100), -- référence externe (credit_id, debt_id...)
    impact INTEGER NOT NULL, -- impact sur le score (-100 à +100)
    description VARCHAR(500),
    event_timestamp TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_score_events_citizen ON trust_score.score_events(citizen_id, event_timestamp DESC) WHERE NOT deleted;
CREATE INDEX idx_score_events_type ON trust_score.score_events(event_type) WHERE NOT deleted;

-- Historique des scores (immuable, pour audit)
CREATE TABLE trust_score.score_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id VARCHAR(100) NOT NULL,
    score_value INTEGER NOT NULL,
    score_level VARCHAR(20) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    source VARCHAR(50) NOT NULL DEFAULT 'COMPUTED', -- COMPUTED, MANUAL_ADJUSTMENT, APPEAL
    notes VARCHAR(500)
);

CREATE INDEX idx_score_history_citizen ON trust_score.score_history(citizen_id, recorded_at DESC);
