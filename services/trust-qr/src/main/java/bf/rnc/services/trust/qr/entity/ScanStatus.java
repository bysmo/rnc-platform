package bf.rnc.services.trust.qr.entity;

/**
 * Statut d'un scan de QR Code.
 */
public enum ScanStatus {
    /** QR scanné — en attente d'autorisation */
    SCANNED,
    /** Autorisé — crédit déclenché ou paiement direct */
    AUTHORIZED,
    /** Refusé (plafond dépassé, score insuffisant, etc.) */
    REJECTED,
    /** Expiré avant autorisation */
    EXPIRED
}
