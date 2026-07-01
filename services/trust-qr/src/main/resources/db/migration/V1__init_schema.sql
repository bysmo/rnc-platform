-- ============================================================
-- Trust QR — QR Code Confiance pour paiements
-- ============================================================
-- Conventions :
--   * QR code signé cryptographiquement (anti-contrefaçon)
--   * QR peut être statique (marchand) ou dynamique (transaction)
--   * Scan trace l'horodatage, IP, géolocalisation (optionnel)
--   * Autorisation en moins de 30 secondes
-- ============================================================

CREATE SCHEMA IF NOT EXISTS trust_qr;

-- ============================================================
-- Table : qr_codes (QR codes générés)
-- ============================================================
CREATE TABLE trust_qr.qr_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    qr_reference VARCHAR(50) NOT NULL UNIQUE,
    qr_type VARCHAR(20) NOT NULL, -- STATIC_MERCHANT, DYNAMIC_TRANSACTION
    merchant_id VARCHAR(100) NOT NULL,
    service_point_id VARCHAR(100),
    citizen_id VARCHAR(100), -- null si STATIC_MERCHANT
    payload_encrypted TEXT NOT NULL, -- contenu chiffré
    payload_hash VARCHAR(128) NOT NULL, -- hash pour vérification
    signature VARCHAR(500) NOT NULL, -- signature ECDSA du payload
    image_url VARCHAR(500),
    image_base64 TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, USED, EXPIRED, REVOKED
    expires_at TIMESTAMPTZ, -- null pour STATIC_MERCHANT
    max_uses INTEGER, -- null = illimité
    use_count INTEGER NOT NULL DEFAULT 0,
    max_amount_minor BIGINT, -- plafond par transaction
    category VARCHAR(30) NOT NULL, -- SCHOOL, HEALTH, FARMING, MERCHANT, SERVICE
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_qr_type CHECK (qr_type IN ('STATIC_MERCHANT','DYNAMIC_TRANSACTION')),
    CONSTRAINT chk_qr_status CHECK (status IN ('ACTIVE','USED','EXPIRED','REVOKED','PENDING')),
    CONSTRAINT chk_qr_category CHECK (category IN ('SCHOOL','HEALTH','FARMING','MERCHANT','INSURANCE','SERVICE'))
);

CREATE INDEX idx_qr_reference ON trust_qr.qr_codes(qr_reference) WHERE NOT deleted;
CREATE INDEX idx_qr_merchant ON trust_qr.qr_codes(merchant_id, status) WHERE NOT deleted;
CREATE INDEX idx_qr_citizen ON trust_qr.qr_codes(citizen_id) WHERE NOT deleted AND citizen_id IS NOT NULL;
CREATE INDEX idx_qr_expires ON trust_qr.qr_codes(expires_at) WHERE NOT deleted AND status = 'ACTIVE' AND expires_at IS NOT NULL;
CREATE INDEX idx_qr_hash ON trust_qr.qr_codes(payload_hash) WHERE NOT deleted;

-- ============================================================
-- Table : qr_scans (journal des scans — audit)
-- ============================================================
CREATE TABLE trust_qr.qr_scans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    qr_code_id UUID NOT NULL REFERENCES trust_qr.qr_codes(id),
    citizen_id VARCHAR(100) NOT NULL,
    scan_reference VARCHAR(50) NOT NULL UNIQUE,
    scanned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    geolocation JSONB, -- {lat, lng} optionnel
    amount_minor BIGINT, -- montant demandé par le citoyen
    currency VARCHAR(3) DEFAULT 'XOF',
    status VARCHAR(20) NOT NULL DEFAULT 'SCANNED', -- SCANNED, AUTHORIZED, REJECTED, EXPIRED
    rejection_reason VARCHAR(500),
    credit_id VARCHAR(100), -- si crédit déclenché
    escrow_account_id VARCHAR(100),
    authorized_at TIMESTAMPTZ,
    authorized_by VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scans_qr ON trust_qr.qr_scans(qr_code_id, scanned_at DESC);
CREATE INDEX idx_scans_citizen ON trust_qr.qr_scans(citizen_id, scanned_at DESC);
CREATE INDEX idx_scans_status ON trust_qr.qr_scans(status, scanned_at DESC);
CREATE INDEX idx_scans_merchant ON trust_qr.qr_scans(qr_code_id) WHERE status = 'AUTHORIZED';

-- ============================================================
-- Table : merchant_authorizations (autorisation préalable marchand)
-- ============================================================
CREATE TABLE trust_qr.merchant_authorizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id VARCHAR(100) NOT NULL,
    citizen_id VARCHAR(100) NOT NULL,
    max_amount_minor BIGINT NOT NULL,
    used_amount_minor BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoked_reason VARCHAR(500),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_auth_status CHECK (status IN ('ACTIVE','EXPIRED','REVOKED','EXHAUSTED'))
);

CREATE INDEX idx_auth_merchant_citizen ON trust_qr.merchant_authorizations(merchant_id, citizen_id, status)
    WHERE NOT deleted AND status = 'ACTIVE';
CREATE INDEX idx_auth_expires ON trust_qr.merchant_authorizations(expires_at)
    WHERE NOT deleted AND status = 'ACTIVE' AND expires_at IS NOT NULL;

-- ============================================================
-- Table : outbox_events
-- ============================================================
CREATE TABLE trust_qr.outbox_events (
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

CREATE INDEX idx_outbox_unpublished ON trust_qr.outbox_events(created_at) WHERE published_at IS NULL;
