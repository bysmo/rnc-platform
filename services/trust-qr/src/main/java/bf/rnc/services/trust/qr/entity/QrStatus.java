package bf.rnc.services.trust.qr.entity;

/**
 * Statut d'un QR Code.
 */
public enum QrStatus {
    /** Actif — peut être scanné */
    ACTIVE,
    /** Utilisé (pour les dynamiques à usage unique) */
    USED,
    /** Expiré */
    EXPIRED,
    /** Révoqué par l'admin ou le marchand */
    REVOKED,
    /** En attente de validation */
    PENDING
}
