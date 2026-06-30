-- ============================================================
-- Trust Escrow — Compte d'affectation des financements
-- ============================================================
CREATE SCHEMA IF NOT EXISTS trust_escrow;

CREATE TABLE trust_escrow.accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_reference VARCHAR(50) NOT NULL UNIQUE,
    credit_id VARCHAR(100) NOT NULL,
    citizen_id VARCHAR(100) NOT NULL,
    merchant_id VARCHAR(100) NOT NULL,
    purpose VARCHAR(50) NOT NULL, -- SCHOOL, HEALTH, FARMING, MERCHANT
    total_reserved_minor BIGINT NOT NULL CHECK (total_reserved_minor > 0),
    total_released_minor BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'XOF',
    status VARCHAR(30) NOT NULL DEFAULT 'RESERVED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_escrow_status CHECK (status IN ('RESERVED','PARTIALLY_RELEASED','FULLY_RELEASED','REFUNDED','DISPUTED'))
);

CREATE INDEX idx_escrow_credit ON trust_escrow.accounts(credit_id) WHERE NOT deleted;
CREATE INDEX idx_escrow_merchant ON trust_escrow.accounts(merchant_id) WHERE NOT deleted;
CREATE INDEX idx_escrow_citizen ON trust_escrow.accounts(citizen_id) WHERE NOT deleted;

-- Règles de déblocage
CREATE TABLE trust_escrow.release_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escrow_account_id UUID NOT NULL REFERENCES trust_escrow.accounts(id),
    sequence INTEGER NOT NULL,
    release_type VARCHAR(30) NOT NULL, -- IMMEDIATE, ON_DELIVERY, ON_REPAYMENT, ON_VALIDATION
    percentage INTEGER NOT NULL CHECK (percentage > 0 AND percentage <= 100),
    condition_description VARCHAR(500),
    condition_metadata JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, MET, RELEASED, CANCELLED
    met_at TIMESTAMPTZ,
    released_at TIMESTAMPTZ,
    released_amount_minor BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_release_rules_escrow ON trust_escrow.release_rules(escrow_account_id, sequence) WHERE NOT deleted;

-- Transactions escrow (immuable)
CREATE TABLE trust_escrow.transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_reference VARCHAR(50) NOT NULL UNIQUE,
    escrow_account_id UUID NOT NULL REFERENCES trust_escrow.accounts(id),
    transaction_type VARCHAR(30) NOT NULL, -- RESERVE, RELEASE, REFUND, REVERSAL
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'XOF',
    direction VARCHAR(10) NOT NULL, -- DEBIT, CREDIT
    reason VARCHAR(500),
    related_release_rule_id UUID,
    metadata JSONB,
    transaction_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_escrow_tx_account ON trust_escrow.transactions(escrow_account_id, transaction_at DESC);
CREATE INDEX idx_escrow_tx_type ON trust_escrow.transactions(transaction_type);
