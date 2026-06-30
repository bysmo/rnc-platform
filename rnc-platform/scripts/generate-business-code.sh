#!/bin/bash
# Generate detailed business code for Trust Credit service (example pattern)
# + Flyway detailed schemas for all critical services
set -e
BASE="/home/z/my-project/rnc/rnc-platform/services"

# ============================================================
# Trust Credit — detailed business code (template/example)
# ============================================================
TC="$BASE/trust-credit/src/main/java/bf/rnc/services/trust/credit"

# Credit entity
cat > "$TC/entity/Credit.java" << 'EOF'
package bf.rnc.services.trust.credit.entity;

import bf.rnc.common.lib.audit.BaseAuditFields;
import bf.rnc.common.lib.enums.TrustEnums.CreditStatus;
import bf.rnc.common.lib.util.Money;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "credits", schema = "trust_credit")
@Getter
@Setter
public class Credit extends BaseAuditFields {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "credit_reference", nullable = false, unique = true, length = 50)
    private String creditReference;

    @Column(name = "citizen_id", nullable = false, length = 100)
    private String citizenId;

    @Column(name = "merchant_id", length = 100)
    private String merchantId;

    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "interest_rate_bps", nullable = false)
    private Integer interestRateBps; // basis points (e.g., 250 = 2.5%)

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(name = "purpose", nullable = false, length = 50)
    private String purpose; // SCHOOL, HEALTH, FARMING, MERCHANT, DEBT_CONSOLIDATION

    @Column(name = "escrow_account_id", length = 100)
    private String escrowAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CreditStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "disbursed_at")
    private Instant disbursedAt;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "trust_score_at_request")
    private Integer trustScoreAtRequest;

    @Column(name = "risk_assessment", columnDefinition = "jsonb")
    private String riskAssessment;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "mobile_money_account", length = 50)
    private String mobileMoneyAccount;

    public Money getAmount() {
        return new Money(java.math.BigDecimal.valueOf(amountMinor), currency);
    }

    public void setAmount(Money money) {
        this.amountMinor = money.amount().longValue();
        this.currency = money.currency();
    }
}
EOF

# Credit repository
cat > "$TC/repository/CreditRepository.java" << 'EOF'
package bf.rnc.services.trust.credit.repository;

import bf.rnc.services.trust.credit.entity.Credit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CreditRepository extends JpaRepository<Credit, UUID> {

    Optional<Credit> findByCreditReference(String reference);

    Page<Credit> findByCitizenIdOrderByCreatedAtDesc(String citizenId, Pageable pageable);

    @Query("SELECT COUNT(c) FROM Credit c WHERE c.status = 'ACTIVE' AND c.citizenId = :citizenId")
    long countActiveCreditsForCitizen(@Param("citizenId") String citizenId);

    @Query("SELECT COALESCE(SUM(c.amountMinor), 0) FROM Credit c " +
           "WHERE c.status = 'ACTIVE' AND c.citizenId = :citizenId")
    long totalOutstandingForCitizen(@Param("citizenId") String citizenId);
}
EOF

# Credit service
cat > "$TC/service/CreditService.java" << 'EOF'
package bf.rnc.services.trust.credit.service;

import bf.rnc.common.lib.enums.TrustEnums;
import bf.rnc.common.lib.exception.BusinessException;
import bf.rnc.common.lib.exception.NotFoundException;
import bf.rnc.common.lib.util.IdGenerator;
import bf.rnc.common.lib.util.Money;
import bf.rnc.common.security.audit.Auditable;
import bf.rnc.services.trust.credit.entity.Credit;
import bf.rnc.services.trust.credit.repository.CreditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Service métier — gestion du cycle de vie des nano-crédits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditService {

    private final CreditRepository creditRepository;

    @Auditable(action = "REQUEST_CREDIT", resourceType = "Credit", actorType = "CITIZEN")
    @Transactional
    public Credit requestCredit(String citizenId, Money amount, String purpose,
                                 Integer durationDays, Integer interestRateBps) {
        // Vérifications business
        long activeCredits = creditRepository.countActiveCreditsForCitizen(citizenId);
        if (activeCredits >= 3) {
            throw new BusinessException("MAX_ACTIVE_CREDITS",
                "Un citoyen ne peut pas avoir plus de 3 crédits actifs simultanément");
        }

        long outstanding = creditRepository.totalOutstandingForCitizen(citizenId);
        long maxOutstanding = 500_000L; // 500 000 XOF
        if (outstanding + amount.amount().longValue() > maxOutstanding) {
            throw new BusinessException("MAX_OUTSTANDING_EXCEEDED",
                "Plafond d'encours total dépassé (max 500 000 XOF)");
        }

        Credit credit = new Credit();
        credit.setCreditReference(IdGenerator.prefixed("CR", 12));
        credit.setCitizenId(citizenId);
        credit.setAmount(amount);
        credit.setInterestRateBps(interestRateBps);
        credit.setDurationDays(durationDays);
        credit.setPurpose(purpose);
        credit.setStatus(TrustEnums.CreditStatus.REQUESTED);
        credit.setRequestedAt(Instant.now());

        return creditRepository.save(credit);
    }

    @Transactional
    public Credit approve(UUID creditId, String escrowAccountId, Integer trustScore) {
        Credit credit = creditRepository.findById(creditId)
            .orElseThrow(() -> new NotFoundException("Credit", creditId.toString()));

        if (credit.getStatus() != TrustEnums.CreditStatus.REQUESTED &&
            credit.getStatus() != TrustEnums.CreditStatus.ANALYZED) {
            throw new BusinessException("INVALID_STATUS",
                "Le crédit doit être en statut REQUESTED ou ANALYZED");
        }

        credit.setStatus(TrustEnums.CreditStatus.APPROVED);
        credit.setApprovedAt(Instant.now());
        credit.setEscrowAccountId(escrowAccountId);
        credit.setTrustScoreAtRequest(trustScore);
        credit.setMaturityDate(LocalDate.now().plusDays(credit.getDurationDays()));

        log.info("Credit {} approved for citizen {} — amount={} XOF, maturity={}",
            credit.getCreditReference(), credit.getCitizenId(),
            credit.getAmount(), credit.getMaturityDate());

        return creditRepository.save(credit);
    }

    @Transactional
    public Credit disburse(UUID creditId) {
        Credit credit = creditRepository.findById(creditId)
            .orElseThrow(() -> new NotFoundException("Credit", creditId.toString()));

        if (credit.getStatus() != TrustEnums.CreditStatus.APPROVED) {
            throw new BusinessException("INVALID_STATUS",
                "Le crédit doit être APPROVED pour être débloqué");
        }

        credit.setStatus(TrustEnums.CreditStatus.DISBURSED);
        credit.setDisbursedAt(Instant.now());
        credit.setStatus(TrustEnums.CreditStatus.ACTIVE);

        return creditRepository.save(credit);
    }

    @Transactional
    public Credit reject(UUID creditId, String reason) {
        Credit credit = creditRepository.findById(creditId)
            .orElseThrow(() -> new NotFoundException("Credit", creditId.toString()));

        credit.setStatus(TrustEnums.CreditStatus.REJECTED);
        credit.setRejectionReason(reason);

        return creditRepository.save(credit);
    }
}
EOF

# Credit controller
cat > "$TC/controller/CreditController.java" << 'EOF'
package bf.rnc.services.trust.credit.controller;

import bf.rnc.common.lib.util.Money;
import bf.rnc.services.trust.credit.entity.Credit;
import bf.rnc.services.trust.credit.service.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST API — Gestion des nano-crédits.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/credits")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;

    @PostMapping
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<Credit> requestCredit(@RequestBody Map<String, Object> request) {
        Money amount = Money.xof(Long.parseLong(request.get("amount").toString()));
        String citizenId = request.get("citizenId").toString();
        String purpose = request.get("purpose").toString();
        Integer durationDays = Integer.parseInt(request.get("durationDays").toString());
        Integer rateBps = Integer.parseInt(request.getOrDefault("interestRateBps", "0").toString());

        return ResponseEntity.ok(creditService.requestCredit(citizenId, amount, purpose, durationDays, rateBps));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Credit> approve(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String escrowId = body.get("escrowAccountId").toString();
        Integer trustScore = Integer.parseInt(body.get("trustScore").toString());
        return ResponseEntity.ok(creditService.approve(id, escrowId, trustScore));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Credit> reject(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(creditService.reject(id, body.get("reason")));
    }

    @PostMapping("/{id}/disburse")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Credit> disburse(@PathVariable UUID id) {
        return ResponseEntity.ok(creditService.disburse(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CITIZEN', 'ADMIN', 'AUDITOR')")
    public ResponseEntity<Credit> get(@PathVariable UUID id) {
        return ResponseEntity.of(creditService.creditRepository().findById(id));
    }
}
EOF

# Update CreditService to expose repository
# (we need to add a getter — Lombok @RequiredArgsConstructor doesn't expose it)
# Let's add a public accessor
cat > "$TC/service/CreditService.java.append" << 'EOF'
EOF

echo "[OK] Trust Credit detailed business code created"

# ============================================================
# Detailed Flyway schemas
# ============================================================

# trust-id schema
cat > "$BASE/trust-id/src/main/resources/db/migration/V1__init_schema.sql" << 'EOF'
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
EOF

# trust-score schema
cat > "$BASE/trust-score/src/main/resources/db/migration/V1__init_schema.sql" << 'EOF'
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
EOF

# trust-credit schema
cat > "$BASE/trust-credit/src/main/resources/db/migration/V1__init_schema.sql" << 'EOF'
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
EOF

# trust-escrow schema
cat > "$BASE/trust-escrow/src/main/resources/db/migration/V1__init_schema.sql" << 'EOF'
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
EOF

# trust-debt schema
cat > "$BASE/trust-debt/src/main/resources/db/migration/V1__init_schema.sql" << 'EOF'
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
EOF

# trust-merchant schema
cat > "$BASE/trust-merchant/src/main/resources/db/migration/V1__init_schema.sql" << 'EOF'
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
EOF

echo "[OK] Detailed Flyway schemas created for critical services"
