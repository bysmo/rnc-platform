-- ============================================================
-- Trust Debt — Reconnaissance de dette entre particuliers
-- ============================================================
CREATE SCHEMA IF NOT EXISTS trust_debt;

CREATE TABLE trust_debt.debts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    debt_reference VARCHAR(50) NOT NULL UNIQUE,
    lender_citizen_id VARCHAR(100) NOT NULL,
    borrower_citizen_id VARCHAR(100) NOT NULL,
    principal_minor BIGINT NOT NULL CHECK (principal_minor > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'XOF',
    interest_rate_bps INTEGER NOT NULL DEFAULT 0,
    duration_days INTEGER NOT NULL CHECK (duration_days > 0 AND duration_days <= 3650),
    maturity_date DATE NOT NULL,
    purpose VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    lender_signed_at TIMESTAMPTZ,
    borrower_signed_at TIMESTAMPTZ,
    signed_at TIMESTAMPTZ, -- quand les deux ont signé
    blockchain_hash VARCHAR(128), -- hash de l'accord (preuve d'intégrité)
    nfc_signature_hash VARCHAR(128), -- si signature biométrique/NFC
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_debt_status CHECK (status IN ('DRAFT','SIGNED','ACTIVE','HONORED','PARTIALLY_HONORED','OVERDUE','DEFAULTED','DISPUTED','CANCELLED')),
    CONSTRAINT chk_lender_borrower CHECK (lender_citizen_id != borrower_citizen_id)
);

CREATE INDEX idx_debts_lender ON trust_debt.debts(lender_citizen_id, created_at DESC) WHERE NOT deleted;
CREATE INDEX idx_debts_borrower ON trust_debt.debts(borrower_citizen_id, created_at DESC) WHERE NOT deleted;
CREATE INDEX idx_debts_status ON trust_debt.debts(status) WHERE NOT deleted;
CREATE INDEX idx_debts_maturity ON trust_debt.debts(maturity_date) WHERE NOT deleted AND status IN ('SIGNED','ACTIVE','OVERDUE');

-- Paiements partiels remboursement dette
CREATE TABLE trust_debt.repayments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repayment_reference VARCHAR(50) NOT NULL UNIQUE,
    debt_id UUID NOT NULL REFERENCES trust_debt.debts(id),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'XOF',
    paid_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    payment_channel VARCHAR(30) NOT NULL,
    payment_proof_url VARCHAR(500),
    lender_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    borrower_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    confirmed_at TIMESTAMPTZ,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_debt_repayments_debt ON trust_debt.repayments(debt_id, paid_at DESC) WHERE NOT deleted;
