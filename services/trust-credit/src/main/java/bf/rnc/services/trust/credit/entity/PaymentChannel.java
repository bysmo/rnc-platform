package bf.rnc.services.trust.credit.entity;

/**
 * Canal de paiement d'un remboursement.
 */
public enum PaymentChannel {
    MOBILE_MONEY,
    BANK_TRANSFER,
    ESCROW_RELEASE,
    CASH,
    CARD
}
