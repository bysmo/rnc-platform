package bf.rnc.services.trust.credit.entity;

import bf.rnc.common.lib.audit.BaseAuditFields;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Nano-crédit RNC.
 *
 * <p>Le crédit passe par les statuts suivants :</p>
 * <pre>
 * REQUESTED → ANALYZED → APPROVED → DISBURSED → ACTIVE → COMPLETED
 *                 ↓          ↓
 *             REJECTED   CANCELLED
 *                            ↑
 *                          ACTIVE → DEFAULTED (si retard > 30j)
 * </pre>
 */
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
    private String currency = "XOF";

    /** Taux d'intérêt en points de base (100 bps = 1%) */
    @Column(name = "interest_rate_bps", nullable = false)
    private Integer interestRateBps = 0;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 50)
    private CreditPurpose purpose;

    @Column(name = "escrow_account_id", length = 100)
    private String escrowAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CreditStatus status = CreditStatus.REQUESTED;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "disbursed_at")
    private Instant disbursedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "trust_score_at_request")
    private Integer trustScoreAtRequest;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_assessment", columnDefinition = "jsonb")
    private String riskAssessment;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "mobile_money_account", length = 50)
    private String mobileMoneyAccount;

    // ============================================================
    // Helpers métier
    // ============================================================

    public long getAmountXof() {
        return amountMinor != null ? amountMinor : 0L;
    }

    public long getTotalInterestMinor() {
        if (amountMinor == null || interestRateBps == null) return 0;
        return amountMinor * interestRateBps / 10000L;
    }

    public long getTotalRepayableMinor() {
        return getAmountXof() + getTotalInterestMinor();
    }

    public boolean isEditable() {
        return status == CreditStatus.REQUESTED || status == CreditStatus.ANALYZED;
    }

    public boolean isCancellable() {
        return status == CreditStatus.REQUESTED
                || status == CreditStatus.ANALYZED
                || status == CreditStatus.APPROVED;
    }

    public boolean isActive() {
        return status == CreditStatus.ACTIVE || status == CreditStatus.DISBURSED;
    }
}
