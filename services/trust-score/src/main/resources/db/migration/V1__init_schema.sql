-- ============================================================
-- Trust Score — Réputation financière nationale
-- ============================================================
-- Conventions RNC :
--   * Score sur l'échelle 0-1000 (5 niveaux)
--   * Historique immuable pour audit (score_history)
--   * Événements de score détaillés pour explicabilité
--   * Algorithme audité annuellement (conformité réglementaire)
-- ============================================================

CREATE SCHEMA IF NOT EXISTS trust_score;

-- ============================================================
-- Table : scores (score courant par citoyen — 1 ligne active)
-- ============================================================
CREATE TABLE trust_score.scores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id VARCHAR(100) NOT NULL,
    score_value INTEGER NOT NULL CHECK (score_value BETWEEN 0 AND 1000),
    score_level VARCHAR(20) NOT NULL,
    computed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    factors JSONB NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_score_level CHECK (score_level IN ('CRITICAL','LOW','FAIR','GOOD','EXCELLENT'))
);

-- Une seule ligne active (non supprimée) par citoyen
CREATE UNIQUE INDEX idx_scores_citizen_current ON trust_score.scores(citizen_id) WHERE NOT deleted;
CREATE INDEX idx_scores_citizen ON trust_score.scores(citizen_id, computed_at DESC) WHERE NOT deleted;
CREATE INDEX idx_scores_level ON trust_score.scores(score_level) WHERE NOT deleted;
CREATE INDEX idx_scores_value ON trust_score.scores(score_value) WHERE NOT deleted;

-- ============================================================
-- Table : score_events (événements ayant impacté le score)
-- ============================================================
CREATE TABLE trust_score.score_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_reference VARCHAR(100),
    impact INTEGER NOT NULL,
    description VARCHAR(500),
    event_timestamp TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_event_impact CHECK (impact BETWEEN -500 AND 500)
);

CREATE INDEX idx_score_events_citizen ON trust_score.score_events(citizen_id, event_timestamp DESC) WHERE NOT deleted;
CREATE INDEX idx_score_events_type ON trust_score.score_events(event_type) WHERE NOT deleted;
CREATE INDEX idx_score_events_reference ON trust_score.score_events(event_reference) WHERE NOT deleted AND event_reference IS NOT NULL;

-- ============================================================
-- Table : score_history (historique immuable pour audit)
-- ============================================================
CREATE TABLE trust_score.score_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id VARCHAR(100) NOT NULL,
    score_value INTEGER NOT NULL,
    score_level VARCHAR(20) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    source VARCHAR(50) NOT NULL DEFAULT 'COMPUTED',
    notes VARCHAR(500),

    CONSTRAINT chk_history_source CHECK (source IN ('COMPUTED','MANUAL_ADJUSTMENT','APPEAL','INITIAL'))
);

CREATE INDEX idx_score_history_citizen ON trust_score.score_history(citizen_id, recorded_at DESC);

-- ============================================================
-- Table : score_factors_config (configuration des facteurs de scoring)
-- ============================================================
CREATE TABLE trust_score.score_factors_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    factor_key VARCHAR(50) NOT NULL UNIQUE,
    factor_label VARCHAR(200) NOT NULL,
    weight INTEGER NOT NULL CHECK (weight BETWEEN 0 AND 100),
    description VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- Configuration initiale des facteurs (conformité explicabilité)
INSERT INTO trust_score.score_factors_config (factor_key, factor_label, weight, description) VALUES
    ('CREDIT_REPAYMENT',   'Remboursement des crédits à temps',     40, 'Pondération principale : historique de remboursement'),
    ('DEBT_BETWEEN_PEERS', 'Dettes entre particuliers honorées',    20, 'Respect des engagements informels'),
    ('CREDIT_UTILIZATION', 'Taux d''utilisation du crédit',         15, 'Ratio encours / plafond disponible'),
    ('ACCOUNT_AGE',        'Ancienneté du compte Confiance',        10, 'Stabilité de la relation'),
    ('TRANSACTION_VOLUME', 'Volume transactionnel Mobile Money',    10, 'Activité économique observée'),
    ('KYC_COMPLETION',     'Complétude du KYC',                      5, 'Niveau de vérification d''identité');

-- ============================================================
-- Table : score_appeals (contestations citoyen)
-- ============================================================
CREATE TABLE trust_score.score_appeals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id VARCHAR(100) NOT NULL,
    score_at_appeal INTEGER NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ,
    reviewed_by VARCHAR(100),
    review_notes VARCHAR(1000),
    score_after_review INTEGER,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_appeal_status CHECK (status IN ('PENDING','APPROVED','REJECTED','UNDER_REVIEW'))
);

CREATE INDEX idx_appeals_citizen ON trust_score.score_appeals(citizen_id, submitted_at DESC) WHERE NOT deleted;
CREATE INDEX idx_appeals_status ON trust_score.score_appeals(status) WHERE NOT deleted AND status = 'PENDING';

-- ============================================================
-- Table : outbox_events (pattern transactional outbox)
-- ============================================================
CREATE TABLE trust_score.outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    topic VARCHAR(200) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000)
);

CREATE INDEX idx_outbox_unpublished ON trust_score.outbox_events(created_at) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_aggregate ON trust_score.outbox_events(aggregate_type, aggregate_id);
