package bf.rnc.services.trust.credit.entity;

import bf.rnc.common.lib.audit.BaseAuditFields;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Paiement reçu pour un crédit.
 *
 * <p>Idempotent via {@code idempotencyKey} : un même paiement webhook
 * ne peut pas être enregistré deux fois.</p>
 */
@Entity
@Table(name = "payments", schema = "trust_credit")
@Getter
@Setter
public class Payment extends BaseAuditFields {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_reference", nullable = false, unique = true, length = 50)
    private String paymentReference;

    @Column(name = "credit_id", nullable = false)
    private UUID creditId;

    @Column(name = "installment_id")
    private UUID installmentId;

    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "XOF";

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_channel", nullable = false, length = 30)
    private PaymentChannel paymentChannel;

    @Column(name = "payment_provider", length = 50)
    private String paymentProvider;

    @Column(name = "provider_transaction_id", length = 100)
    private String providerTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "idempotency_key", unique = true, length = 200)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
}
