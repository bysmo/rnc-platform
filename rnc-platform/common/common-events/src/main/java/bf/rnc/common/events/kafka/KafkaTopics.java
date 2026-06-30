package bf.rnc.common.events.kafka;

/**
 * Topics Kafka centralisés pour la plateforme RNC.
 * Tous les microservices utilisent ces constantes pour publier/consommer des events.
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    // Audit & traçabilité (append-only log)
    public static final String AUDIT_EVENTS = "rnc.audit.events";

    // Trust Score
    public static final String SCORE_UPDATED = "rnc.trust-score.updated";
    public static final String SCORE_RECALCULATED = "rnc.trust-score.recalculated";

    // Credit lifecycle
    public static final String CREDIT_REQUESTED = "rnc.credit.requested";
    public static final String CREDIT_APPROVED = "rnc.credit.approved";
    public static final String CREDIT_REJECTED = "rnc.credit.rejected";
    public static final String CREDIT_DISBURSED = "rnc.credit.disbursed";
    public static final String CREDIT_REPAID = "rnc.credit.repaid";
    public static final String CREDIT_DEFAULTED = "rnc.credit.defaulted";

    // Escrow
    public static final String ESCROW_RESERVED = "rnc.escrow.reserved";
    public static final String ESCROW_RELEASED = "rnc.escrow.released";

    // Debt (reconnaissance de dette)
    public static final String DEBT_SIGNED = "rnc.debt.signed";
    public static final String DEBT_HONORED = "rnc.debt.honored";
    public static final String DEBT_OVERDUE = "rnc.debt.overdue";

    // QR Code
    public static final String QR_SCANNED = "rnc.qr.scanned";
    public static final String QR_AUTHORIZED = "rnc.qr.authorized";

    // Identity
    public static final String USER_REGISTERED = "rnc.identity.registered";
    public static final String KYC_VERIFIED = "rnc.identity.kyc-verified";

    // Notifications (consumed by notification service)
    public static final String NOTIFICATION_REQUESTED = "rnc.notification.requested";
}
