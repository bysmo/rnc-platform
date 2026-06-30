-- ============================================================
-- Trust Credit — Nano-crédit instantané
-- ============================================================
CREATE SCHEMA IF NOT EXISTS trust_credit;

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
    CONSTRAINT chk_credit_status CHECK (status IN ('REQUESTED','ANALYZED','APPROVED','REJECTED','DISBURSED','ACTIVE','COMPLETED','DEFAULTED','CANCELLED')),
    CONSTRAINT chk_purpose CHECK (purpose IN ('SCHOOL','HEALTH','FARMING','MERCHANT','DEBT_CONSOLIDATION','EMERGENCY','OTHER'))
);

CREATE INDEX idx_credits_citizen ON trust_credit.credits(citizen_id, created_at DESC) WHERE NOT deleted;
CREATE INDEX idx_credits_status ON trust_credit.credits(status) WHERE NOT deleted;
CREATE INDEX idx_credits_merchant ON trust_credit.credits(merchant_id) WHERE NOT deleted AND merchant_id IS NOT NULL;
CREATE INDEX idx_credits_maturity ON trust_credit.credits(maturity_date) WHERE NOT deleted AND status = 'ACTIVE';
CREATE INDEX idx_credits_reference ON trust_credit.credits(credit_reference) WHERE NOT deleted;

-- Échéancier de remboursement
CREATE TABLE trust_credit.repayment_schedule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credit_id UUID NOT NULL REFERENCES trust_credit.credits(id),
    installment_number INTEGER NOT NULL,
    due_date DATE NOT NULL,
    principal_minor BIGINT NOT NULL,
    interest_minor BIGINT NOT NULL,
    total_minor BIGINT NOT NULL,
    paid_minor BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, PAID, PARTIALLY_PAID, OVERDUE
    paid_at TIMESTAMPTZ,
    payment_method VARCHAR(30),
    payment_reference VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(credit_id, installment_number)
);

CREATE INDEX idx_schedule_credit ON trust_credit.repayment_schedule(credit_id, installment_number) WHERE NOT deleted;
CREATE INDEX idx_schedule_due ON trust_credit.repayment_schedule(due_date, status) WHERE NOT deleted AND status IN ('PENDING','PARTIALLY_PAID');

-- Paiements
CREATE TABLE trust_credit.payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_reference VARCHAR(50) NOT NULL UNIQUE,
    credit_id UUID NOT NULL REFERENCES trust_credit.credits(id),
    installment_id UUID REFERENCES trust_credit.repayment_schedule(id),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'XOF',
    payment_channel VARCHAR(30) NOT NULL, -- MOBILE_MONEY, BANK_TRANSFER, ESCROW_RELEASE
    payment_provider VARCHAR(50), -- ORANGE_MONEY, MOOV_MONEY, TELECEL_MONEY
    provider_transaction_id VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, COMPLETED, FAILED, REFUNDED
    paid_at TIMESTAMPTZ,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_payments_credit ON trust_credit.payments(credit_id) WHERE NOT deleted;
CREATE INDEX idx_payments_status ON trust_credit.payments(status) WHERE NOT deleted;
CREATE INDEX idx_payments_paid_at ON trust_credit.payments(paid_at) WHERE NOT deleted;
