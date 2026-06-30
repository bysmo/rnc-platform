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
