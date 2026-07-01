-- ============================================================
-- Trust Credit — Nano-crédit instantané
-- ============================================================
-- Conventions RNC :
--   * Montants en XOF mineur (1 XOF = 1 unité, pas de centimes)
--   * Taux d'intérêt en points de base (bps) — 100 bps = 1%
--   * Durée max 365 jours (nano-crédit court terme)
--   * Encours max par citoyen : 500 000 XOF (règle métier)
--   * Max 3 crédits actifs simultanés par citoyen
-- ============================================================

CREATE SCHEMA IF NOT EXISTS trust_credit;

-- ============================================================
-- Table : credits
-- ============================================================
CREATE TABLE trust_credit.credits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credit_reference VARCHAR(50) NOT NULL UNIQUE,
    citizen_id VARCHAR(100) NOT NULL,
    merchant_id VARCHAR(100),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'XOF',
    interest_rate_bps INTEGER NOT NULL DEFAULT 0 CHECK (interest_rate_bps >= 0 AND interest_rate_bps < 10000),
    duration_days INTEGER NOT NULL CHECK (duration_days > 0 AND duration_days <= 365),
    purpose VARCHAR(50) NOT NULL,
    escrow_account_id VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    approved_at TIMESTAMPTZ,
    disbursed_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    maturity_date DATE,
    trust_score_at_request INTEGER,
    risk_assessment JSONB,
    rejection_reason VARCHAR(500),
    mobile_money_account VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_credit_status CHECK (status IN (
        'REQUESTED','ANALYZED','APPROVED','REJECTED','DISBURSED',
        'ACTIVE','COMPLETED','DEFAULTED','CANCELLED'
    )),
    CONSTRAINT chk_purpose CHECK (purpose IN (
        'SCHOOL','HEALTH','FARMING','MERCHANT','DEBT_CONSOLIDATION',
        'EMERGENCY','EQUIPMENT','OTHER'
    )),
    CONSTRAINT chk_amount_max CHECK (amount_minor <= 500000)
);

CREATE INDEX idx_credits_citizen ON trust_credit.credits(citizen_id, created_at DESC) WHERE NOT deleted;
CREATE INDEX idx_credits_status ON trust_credit.credits(status) WHERE NOT deleted;
CREATE INDEX idx_credits_merchant ON trust_credit.credits(merchant_id) WHERE NOT deleted AND merchant_id IS NOT NULL;
CREATE INDEX idx_credits_maturity ON trust_credit.credits(maturity_date) WHERE NOT deleted AND status = 'ACTIVE';
CREATE INDEX idx_credits_reference ON trust_credit.credits(credit_reference) WHERE NOT deleted;
CREATE INDEX idx_credits_requested ON trust_credit.credits(requested_at DESC) WHERE NOT deleted;

-- ============================================================
-- Table : repayment_schedule (échéancier)
-- ============================================================
CREATE TABLE trust_credit.repayment_schedule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credit_id UUID NOT NULL REFERENCES trust_credit.credits(id),
    installment_number INTEGER NOT NULL,
    due_date DATE NOT NULL,
    principal_minor BIGINT NOT NULL CHECK (principal_minor >= 0),
    interest_minor BIGINT NOT NULL DEFAULT 0 CHECK (interest_minor >= 0),
    total_minor BIGINT NOT NULL CHECK (total_minor >= 0),
    paid_minor BIGINT NOT NULL DEFAULT 0 CHECK (paid_minor >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    paid_at TIMESTAMPTZ,
    payment_method VARCHAR(30),
    payment_reference VARCHAR(100),
    grace_period_days INTEGER NOT NULL DEFAULT 0,
    reminder_sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(credit_id, installment_number),

    CONSTRAINT chk_installment_status CHECK (status IN (
        'PENDING','PAID','PARTIALLY_PAID','OVERDUE','CANCELLED'
    )),
    CONSTRAINT chk_installment_total CHECK (total_minor = principal_minor + interest_minor)
);

CREATE INDEX idx_schedule_credit ON trust_credit.repayment_schedule(credit_id, installment_number) WHERE NOT deleted;
CREATE INDEX idx_schedule_due ON trust_credit.repayment_schedule(due_date, status)
    WHERE NOT deleted AND status IN ('PENDING','PARTIALLY_PAID');
CREATE INDEX idx_schedule_status ON trust_credit.repayment_schedule(status) WHERE NOT deleted;

-- ============================================================
-- Table : payments (paiements reçus)
-- ============================================================
CREATE TABLE trust_credit.payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_reference VARCHAR(50) NOT NULL UNIQUE,
    credit_id UUID NOT NULL REFERENCES trust_credit.credits(id),
    installment_id UUID REFERENCES trust_credit.repayment_schedule(id),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'XOF',
    payment_channel VARCHAR(30) NOT NULL,
    payment_provider VARCHAR(50),
    provider_transaction_id VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    paid_at TIMESTAMPTZ,
    processed_at TIMESTAMPTZ,
    failure_reason VARCHAR(500),
    idempotency_key VARCHAR(200) UNIQUE,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_payment_status CHECK (status IN (
        'PENDING','COMPLETED','FAILED','REFUNDED','CANCELLED'
    )),
    CONSTRAINT chk_payment_channel CHECK (payment_channel IN (
        'MOBILE_MONEY','BANK_TRANSFER','ESCROW_RELEASE','CASH','CARD'
    ))
);

CREATE INDEX idx_payments_credit ON trust_credit.payments(credit_id, paid_at DESC) WHERE NOT deleted;
CREATE INDEX idx_payments_status ON trust_credit.payments(status) WHERE NOT deleted AND status = 'PENDING';
CREATE INDEX idx_payments_paid_at ON trust_credit.payments(paid_at) WHERE NOT deleted AND status = 'COMPLETED';
CREATE INDEX idx_payments_provider ON trust_credit.payments(payment_provider, provider_transaction_id)
    WHERE NOT deleted AND provider_transaction_id IS NOT NULL;

-- ============================================================
-- Table : credit_events (audit du cycle de vie — immutable)
-- ============================================================
CREATE TABLE trust_credit.credit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credit_id UUID NOT NULL REFERENCES trust_credit.credits(id),
    event_type VARCHAR(50) NOT NULL,
    event_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor_type VARCHAR(30) NOT NULL DEFAULT 'SYSTEM',
    actor_id VARCHAR(100),
    description VARCHAR(500),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_credit_events_credit ON trust_credit.credit_events(credit_id, event_timestamp DESC);
CREATE INDEX idx_credit_events_type ON trust_credit.credit_events(event_type);

-- ============================================================
-- Table : outbox_events (transactional outbox)
-- ============================================================
CREATE TABLE trust_credit.outbox_events (
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

CREATE INDEX idx_outbox_unpublished ON trust_credit.outbox_events(created_at) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_aggregate ON trust_credit.outbox_events(aggregate_type, aggregate_id);
