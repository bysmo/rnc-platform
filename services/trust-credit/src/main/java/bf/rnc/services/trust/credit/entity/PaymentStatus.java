package bf.rnc.services.trust.credit.entity;

/**
 * Statut d'un paiement.
 */
public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED,
    CANCELLED
}
