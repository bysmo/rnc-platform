-- ============================================================
-- Trust Merchant — Fournisseurs partenaires agréés
-- ============================================================
CREATE SCHEMA IF NOT EXISTS trust_merchant;

CREATE TABLE trust_merchant.merchants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_reference VARCHAR(50) NOT NULL UNIQUE,
    legal_name VARCHAR(255) NOT NULL,
    trade_name VARCHAR(255),
    category VARCHAR(30) NOT NULL, -- SCHOOL, HEALTH, AGRICULTURE, INSURANCE, RETAIL, SERVICE
    nif VARCHAR(50), -- Numéro d'Identification Fiscale
    rc_number VARCHAR(50), -- Registre du Commerce
    phone_number_encrypted VARCHAR(500) NOT NULL,
    phone_number_hash VARCHAR(128) NOT NULL,
    email_encrypted VARCHAR(500),
    email_hash VARCHAR(128),
    address_encrypted VARCHAR(1000),
    region VARCHAR(50) NOT NULL,
    province VARCHAR(50) NOT NULL,
    commune VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, SUSPENDED, REJECTED
    approved_at TIMESTAMPTZ,
    approved_by VARCHAR(100),
    agreement_signed BOOLEAN NOT NULL DEFAULT FALSE,
    agreement_signed_at TIMESTAMPTZ,
    qr_code_seed VARCHAR(128) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_merchant_status CHECK (status IN ('PENDING','APPROVED','SUSPENDED','REJECTED')),
    CONSTRAINT chk_category CHECK (category IN ('SCHOOL','HEALTH','AGRICULTURE','INSURANCE','RETAIL','SERVICE'))
);

CREATE INDEX idx_merchants_phone_hash ON trust_merchant.merchants(phone_number_hash) WHERE NOT deleted;
CREATE INDEX idx_merchants_status ON trust_merchant.merchants(status) WHERE NOT deleted;
CREATE INDEX idx_merchants_category_region ON trust_merchant.merchants(category, region) WHERE NOT deleted AND status = 'APPROVED';

-- Points de service (ex: plusieurs écoles d'une même institution)
CREATE TABLE trust_merchant.service_points (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES trust_merchant.merchants(id),
    name VARCHAR(255) NOT NULL,
    qr_code_value VARCHAR(500) NOT NULL UNIQUE,
    region VARCHAR(50) NOT NULL,
    province VARCHAR(50) NOT NULL,
    commune VARCHAR(50) NOT NULL,
    address_encrypted VARCHAR(1000),
    phone_encrypted VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_service_points_merchant ON trust_merchant.service_points(merchant_id) WHERE NOT deleted;
CREATE UNIQUE INDEX idx_service_points_qr ON trust_merchant.service_points(qr_code_value) WHERE NOT deleted;
