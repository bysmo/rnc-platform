package bf.rnc.services.trust.qr.entity;

/**
 * Type de QR Code.
 */
public enum QrType {
    /** QR Code statique du marchand — réutilisable */
    STATIC_MERCHANT,
    /** QR Code dynamique pour une transaction spécifique — usage unique */
    DYNAMIC_TRANSACTION
}
