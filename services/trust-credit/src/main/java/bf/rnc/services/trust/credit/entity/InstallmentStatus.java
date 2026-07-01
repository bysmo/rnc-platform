package bf.rnc.services.trust.credit.entity;

/**
 * Statut d'une échéance de remboursement.
 */
public enum InstallmentStatus {
    /** En attente de paiement */
    PENDING,
    /** Totalement payée */
    PAID,
    /** Partiellement payée */
    PARTIALLY_PAID,
    /** En retard (échéance dépassée) */
    OVERDUE,
    /** Annulée (ex: crédit restructuré) */
    CANCELLED
}
