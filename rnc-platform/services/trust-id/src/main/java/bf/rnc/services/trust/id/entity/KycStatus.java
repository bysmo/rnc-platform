package bf.rnc.services.trust.id.entity;

/**
 * Statut KYC d'un citoyen.
 */
public enum KycStatus {
    /** Inscription initiale, KYC non encore soumis */
    PENDING,
    /** Documents KYC soumis, en attente de validation agent */
    SUBMITTED,
    /** KYC validé par un agent */
    VERIFIED,
    /** KYC rejeté */
    REJECTED,
    /** KYC expiré (à renouveler après 2 ans) */
    EXPIRED
}
