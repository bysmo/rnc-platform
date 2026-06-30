package bf.rnc.services.trust.id.entity;

/**
 * Statut du compte citoyen.
 */
public enum AccountStatus {
    /** Inscription en cours, OTP non encore vérifié */
    PENDING_ACTIVATION,
    /** Compte actif, OTP vérifié + KYC au minimum PENDING */
    ACTIVE,
    /** Suspendu manuellement par un admin (fraude suspectée, etc.) */
    SUSPENDED,
    /** Compte clôturé — soft delete + anonymisation données personnelles */
    CLOSED
}
