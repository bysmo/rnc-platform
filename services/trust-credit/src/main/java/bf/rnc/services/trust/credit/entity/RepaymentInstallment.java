package bf.rnc.services.trust.credit.entity;

import bf.rnc.common.lib.audit.BaseAuditFields;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Échéance de remboursement d'un crédit.
 *
 * <p>Générée automatiquement à l'approbation du crédit selon la durée et
 * le calendrier (mensuel, hebdomadaire, ou saisonnier pour l'agricole).</p>
 */
@Entity
@Table(name = "repayment_schedule", schema = "trust_credit")
@Getter
@Setter
public class RepaymentInstallment extends BaseAuditFields {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "credit_id", nullable = false)
    private UUID creditId;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "principal_minor", nullable = false)
    private Long principalMinor;

    @Column(name = "interest_minor", nullable = false)
    private Long interestMinor = 0L;

    @Column(name = "total_minor", nullable = false)
    private Long totalMinor;

    @Column(name = "paid_minor", nullable = false)
    private Long paidMinor = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InstallmentStatus status = InstallmentStatus.PENDING;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "grace_period_days", nullable = false)
    private Integer gracePeriodDays = 0;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    // ============================================================
    // Helpers
    // ============================================================

    public long getOutstandingMinor() {
        return totalMinor - paidMinor;
    }

    public boolean isFullyPaid() {
        return paidMinor >= totalMinor;
    }

    public boolean isOverdue(LocalDate today) {
        return status != InstallmentStatus.PAID
                && status != InstallmentStatus.CANCELLED
                && dueDate.plusDays(gracePeriodDays).isBefore(today);
    }
}
