package bf.rnc.services.trust.credit.entity;

/**
 * Statut d'un crédit dans son cycle de vie.
 */
public enum CreditStatus {
    /** Demande initiale soumise par le citoyen */
    REQUESTED,
    /** En cours d'analyse automatique ou manuelle */
    ANALYZED,
    /** Approuvé — prêt à être débloqué */
    APPROVED,
    /** Refusé par le système ou un agent */
    REJECTED,
    /** Débloqué — fonds versés à l'escrow */
    DISBURSED,
    /** Actif — en cours de remboursement */
    ACTIVE,
    /** Totalement remboursé */
    COMPLETED,
    /** En défaut (>30 jours de retard) */
    DEFAULTED,
    /** Annulé par le citoyen ou l'admin */
    CANCELLED
}
