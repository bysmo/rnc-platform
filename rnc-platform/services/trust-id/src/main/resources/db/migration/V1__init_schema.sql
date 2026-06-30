-- ============================================================
-- Trust ID — Identité financière numérique des citoyens
-- ============================================================
-- Conventions RNC :
--   * Champs d'audit standards (created_at, updated_at, created_by, updated_by, deleted, version)
--   * Données personnelles chiffrées AES-256-GCM + hash SHA-256 (pepper) pour recherche
--   * Index systématiques sur clés étrangères et colonnes filtrées
--   * Soft delete pour préserver l'historique (Loi 010-2004/AN)
-- ============================================================

CREATE SCHEMA IF NOT EXISTS trust_id;

-- ============================================================
-- Table principale : citizens
-- ============================================================
CREATE TABLE trust_id.citizens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_reference VARCHAR(50) NOT NULL UNIQUE,
    keycloak_user_id VARCHAR(100) UNIQUE,

    -- Identité civile (chiffrée au repos — Loi 010-2004/AN)
    phone_number_encrypted VARCHAR(500) NOT NULL,
    phone_number_hash VARCHAR(128) NOT NULL,
    phone_number_verified BOOLEAN NOT NULL DEFAULT FALSE,

    email_encrypted VARCHAR(500),
    email_hash VARCHAR(128),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,

    first_name_encrypted VARCHAR(500) NOT NULL,
    last_name_encrypted VARCHAR(500) NOT NULL,
    date_of_birth_encrypted VARCHAR(500),
    gender VARCHAR(10),

    -- Identité Burkina Faso
    cin_number_encrypted VARCHAR(500),
    cin_number_hash VARCHAR(128) UNIQUE,
    cin_issue_date DATE,
    cin_issue_place VARCHAR(100),

    -- Localisation (en clair — non sensible, nécessaire pour analytics)
    region VARCHAR(50) NOT NULL,
    province VARCHAR(50) NOT NULL,
    commune VARCHAR(50) NOT NULL,
    village VARCHAR(100),
    address_encrypted VARCHAR(1000),

    -- KYC / LCB-FT
    kyc_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    kyc_verified_at TIMESTAMPTZ,
    kyc_verified_by VARCHAR(100),
    kyc_rejection_reason VARCHAR(500),

    sanctions_screened BOOLEAN NOT NULL DEFAULT FALSE,
    sanctions_screened_at TIMESTAMPTZ,
    sanctions_hit BOOLEAN NOT NULL DEFAULT FALSE,
    sanctions_details JSONB,

    pep_status VARCHAR(20) NOT NULL DEFAULT 'NOT_CHECKED',
    pep_verified_at TIMESTAMPTZ,

    -- Statut compte
    account_status VARCHAR(20) NOT NULL DEFAULT 'PENDING_ACTIVATION',
    preferred_language VARCHAR(5) NOT NULL DEFAULT 'fr',

    -- Consentement (Loi 010-2004/AN — obligatoire)
    consent_data_processing BOOLEAN NOT NULL DEFAULT FALSE,
    consent_marketing BOOLEAN NOT NULL DEFAULT FALSE,
    consent_data_sharing BOOLEAN NOT NULL DEFAULT FALSE,
    consent_at TIMESTAMPTZ,
    consent_ip VARCHAR(45),

    -- Champs d'audit
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_citizen_ref CHECK (citizen_reference ~ '^CIT-[A-Z0-9]{12}$'),
    CONSTRAINT chk_kyc_status CHECK (kyc_status IN ('PENDING','SUBMITTED','VERIFIED','REJECTED','EXPIRED')),
    CONSTRAINT chk_account_status CHECK (account_status IN ('PENDING_ACTIVATION','ACTIVE','SUSPENDED','CLOSED')),
    CONSTRAINT chk_pep_status CHECK (pep_status IN ('NOT_CHECKED','NOT_PEP','PEP','PEP_FAMILY')),
    CONSTRAINT chk_gender CHECK (gender IS NULL OR gender IN ('M','F','O')),
    CONSTRAINT chk_consent CHECK (consent_data_processing = TRUE),
    CONSTRAINT chk_phone_verified_active CHECK (
        account_status != 'ACTIVE' OR phone_number_verified = TRUE
    )
);

CREATE INDEX idx_citizens_phone_hash ON trust_id.citizens(phone_number_hash) WHERE NOT deleted;
CREATE INDEX idx_citizens_email_hash ON trust_id.citizens(email_hash) WHERE NOT deleted AND email_hash IS NOT NULL;
CREATE INDEX idx_citizens_cin_hash ON trust_id.citizens(cin_number_hash) WHERE NOT deleted AND cin_number_hash IS NOT NULL;
CREATE INDEX idx_citizens_kyc_status ON trust_id.citizens(kyc_status) WHERE NOT deleted;
CREATE INDEX idx_citizens_account_status ON trust_id.citizens(account_status) WHERE NOT deleted;
CREATE INDEX idx_citizens_region ON trust_id.citizens(region, commune) WHERE NOT deleted;
CREATE INDEX idx_citizens_created_at ON trust_id.citizens(created_at);
CREATE INDEX idx_citizens_keycloak ON trust_id.citizens(keycloak_user_id) WHERE keycloak_user_id IS NOT NULL;

-- ============================================================
-- Table : otp_verifications (OTP SMS pour inscription)
-- ============================================================
CREATE TABLE trust_id.otp_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number_hash VARCHAR(128) NOT NULL,
    otp_code_hash VARCHAR(128) NOT NULL, -- hash du code OTP (jamais en clair)
    purpose VARCHAR(30) NOT NULL, -- REGISTRATION, PHONE_CHANGE, LOGIN_MFA, RESET_PASSWORD
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),

    CONSTRAINT chk_otp_purpose CHECK (purpose IN ('REGISTRATION','PHONE_CHANGE','LOGIN_MFA','RESET_PASSWORD'))
);

CREATE INDEX idx_otp_phone ON trust_id.otp_verifications(phone_number_hash, created_at DESC);
CREATE INDEX idx_otp_expires ON trust_id.otp_verifications(expires_at) WHERE NOT verified;
CREATE INDEX idx_otp_purpose ON trust_id.otp_verifications(purpose, created_at DESC);

-- ============================================================
-- Table : kyc_documents (pièces justificatives)
-- ============================================================
CREATE TABLE trust_id.kyc_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id UUID NOT NULL REFERENCES trust_id.citizens(id),
    document_type VARCHAR(30) NOT NULL,
    document_number_encrypted VARCHAR(500),
    document_number_hash VARCHAR(128),
    storage_url VARCHAR(500) NOT NULL,
    storage_bucket VARCHAR(100) NOT NULL DEFAULT 'rnc-kyc-documents',
    storage_key VARCHAR(500) NOT NULL,
    file_sha256 VARCHAR(128) NOT NULL,
    file_size_bytes BIGINT,
    mime_type VARCHAR(100),
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    verified_at TIMESTAMPTZ,
    verified_by VARCHAR(100),
    rejection_reason VARCHAR(500),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_doc_type CHECK (document_type IN ('CIN_RECTO','CIN_VERSO','PASSPORT','BIRTH_CERTIFICATE','PROOF_OF_ADDRESS','SELFIE')),
    CONSTRAINT chk_doc_status CHECK (verification_status IN ('PENDING','APPROVED','REJECTED','EXPIRED'))
);

CREATE INDEX idx_kyc_docs_citizen ON trust_id.kyc_documents(citizen_id) WHERE NOT deleted;
CREATE INDEX idx_kyc_docs_status ON trust_id.kyc_documents(verification_status) WHERE NOT deleted;
CREATE INDEX idx_kyc_docs_hash ON trust_id.kyc_documents(document_number_hash) WHERE NOT deleted AND document_number_hash IS NOT NULL;
CREATE INDEX idx_kyc_docs_submitted ON trust_id.kyc_documents(submitted_at);

-- ============================================================
-- Table : sanctions_screenings (audit LCB-FT)
-- ============================================================
CREATE TABLE trust_id.sanctions_screenings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id UUID NOT NULL REFERENCES trust_id.citizens(id),
    screening_type VARCHAR(30) NOT NULL, -- UN_SANCTIONS, OFAC, EU, PEP_CHECK
    screen_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    matched BOOLEAN NOT NULL DEFAULT FALSE,
    matched_entries JSONB, -- liste des correspondances potentielles
    score INTEGER, -- score de similarité (0-100)
    provider VARCHAR(50),
    raw_response JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_screenings_citizen ON trust_id.sanctions_screenings(citizen_id, screen_date DESC);
CREATE INDEX idx_screenings_matched ON trust_id.sanctions_screenings(matched, screen_date DESC) WHERE matched = TRUE;

-- ============================================================
-- Table : consent_history (historique immuable des consentements)
-- ============================================================
CREATE TABLE trust_id.consent_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id UUID NOT NULL REFERENCES trust_id.citizens(id),
    consent_type VARCHAR(30) NOT NULL, -- DATA_PROCESSING, MARKETING, DATA_SHARING
    granted BOOLEAN NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    source VARCHAR(30) NOT NULL DEFAULT 'MOBILE_APP',

    CONSTRAINT chk_consent_type CHECK (consent_type IN ('DATA_PROCESSING','MARKETING','DATA_SHARING','THIRD_PARTY'))
);

CREATE INDEX idx_consent_citizen ON trust_id.consent_history(citizen_id, granted_at DESC);

-- ============================================================
-- Table : outbox_events (pattern transactional outbox)
-- ============================================================
CREATE TABLE trust_id.outbox_events (
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

CREATE INDEX idx_outbox_unpublished ON trust_id.outbox_events(created_at) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_aggregate ON trust_id.outbox_events(aggregate_type, aggregate_id);
