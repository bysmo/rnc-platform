-- ============================================================
-- Trust ID — Identité financière numérique des citoyens
-- ============================================================
CREATE SCHEMA IF NOT EXISTS trust_id;

CREATE TABLE trust_id.citizens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_reference VARCHAR(50) NOT NULL UNIQUE,
    keycloak_user_id VARCHAR(100) UNIQUE,

    -- Identité civile (Loi 010-2004/AN: données personnelles chiffrées au repos)
    phone_number_encrypted VARCHAR(500) NOT NULL,
    phone_number_hash VARCHAR(128) NOT NULL, -- pour recherche exacte
    email_encrypted VARCHAR(500),
    email_hash VARCHAR(128),

    first_name_encrypted VARCHAR(500) NOT NULL,
    last_name_encrypted VARCHAR(500) NOT NULL,
    date_of_birth_encrypted VARCHAR(500),
    gender VARCHAR(10),

    -- Identité Burkina Faso
    cin_number_encrypted VARCHAR(500), -- Carte d'Identité Nationale
    cin_number_hash VARCHAR(128) UNIQUE,
    cin_issue_date DATE,
    cin_issue_place VARCHAR(100),

    -- Localisation
    region VARCHAR(50) NOT NULL, -- une des 13 régions du Burkina Faso
    province VARCHAR(50) NOT NULL,
    commune VARCHAR(50) NOT NULL,
    village VARCHAR(100),
    address_encrypted VARCHAR(1000),

    -- KYC / LCB-FT
    kyc_status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, VERIFIED, REJECTED
    kyc_verified_at TIMESTAMPTZ,
    kyc_verified_by VARCHAR(100),
    sanctions_screened BOOLEAN NOT NULL DEFAULT FALSE,
    sanctions_screened_at TIMESTAMPTZ,
    pep_status VARCHAR(20) NOT NULL DEFAULT 'NOT_CHECKED', -- Politically Exposed Person

    -- Statut compte
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED, CLOSED
    preferred_language VARCHAR(5) NOT NULL DEFAULT 'fr',
    consent_data_processing BOOLEAN NOT NULL DEFAULT FALSE,
    consent_marketing BOOLEAN NOT NULL DEFAULT FALSE,
    consent_at TIMESTAMPTZ,

    -- Champs d'audit
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_citizen_ref CHECK (citizen_reference ~ '^CIT-[A-Z0-9]{12}$'),
    CONSTRAINT chk_kyc_status CHECK (kyc_status IN ('PENDING','VERIFIED','REJECTED','EXPIRED')),
    CONSTRAINT chk_account_status CHECK (account_status IN ('ACTIVE','SUSPENDED','CLOSED'))
);

CREATE INDEX idx_citizens_phone_hash ON trust_id.citizens(phone_number_hash) WHERE NOT deleted;
CREATE INDEX idx_citizens_email_hash ON trust_id.citizens(email_hash) WHERE NOT deleted AND email_hash IS NOT NULL;
CREATE INDEX idx_citizens_cin_hash ON trust_id.citizens(cin_number_hash) WHERE NOT deleted AND cin_number_hash IS NOT NULL;
CREATE INDEX idx_citizens_kyc_status ON trust_id.citizens(kyc_status) WHERE NOT deleted;
CREATE INDEX idx_citizens_region ON trust_id.citizens(region, commune) WHERE NOT deleted;
CREATE INDEX idx_citizens_created_at ON trust_id.citizens(created_at);

-- Vérifications KYC documentaires
CREATE TABLE trust_id.kyc_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id UUID NOT NULL REFERENCES trust_id.citizens(id),
    document_type VARCHAR(30) NOT NULL, -- CIN, PASSPORT, BIRTH_CERTIFICATE, PROOF_OF_ADDRESS
    document_number_encrypted VARCHAR(500),
    document_hash VARCHAR(128), -- pour déduplication
    storage_url VARCHAR(500), -- URL OSS / S3 du document chiffré
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    verified_at TIMESTAMPTZ,
    verified_by VARCHAR(100),
    rejection_reason VARCHAR(500),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_kyc_docs_citizen ON trust_id.kyc_documents(citizen_id) WHERE NOT deleted;
CREATE INDEX idx_kyc_docs_status ON trust_id.kyc_documents(verification_status) WHERE NOT deleted;
CREATE INDEX idx_kyc_docs_hash ON trust_id.kyc_documents(document_hash) WHERE NOT deleted;

-- Vérifications biométriques (optionnel)
CREATE TABLE trust_id.biometric_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id UUID NOT NULL REFERENCES trust_id.citizens(id),
    biometric_type VARCHAR(20) NOT NULL, -- FINGERPRINT, FACE, VOICE
    template_hash VARCHAR(128) NOT NULL, -- hash du template biométrique
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    verified_at TIMESTAMPTZ,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_biometric_citizen ON trust_id.biometric_verifications(citizen_id) WHERE NOT deleted;
