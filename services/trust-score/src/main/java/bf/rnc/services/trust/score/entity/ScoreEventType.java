package bf.rnc.services.trust.score.entity;

/**
 * Types d'événements impactant le Trust Score.
 *
 * <p>Chaque type a un impact prédéfini (en points) sur le score.
 * Les impacts positifs améliorent le score, les négatifs le dégradent.</p>
 */
public enum ScoreEventType {

    // ─── Événements positifs ─────────────────────────────────────
    CREDIT_REPAID_ON_TIME("Remboursement de crédit à temps", 20),
    CREDIT_REPAID_EARLY("Remboursement anticipé", 25),
    DEBT_HONORED("Dette entre particuliers honorée", 15),
    DEBT_HONORED_EARLY("Dette entre particuliers honorée avant échéance", 20),
    KYC_VERIFIED("Vérification KYC complétée", 30),
    MOBILE_MONEY_ACTIVE("Activité Mobile Money régulière (3+ mois)", 10),
    LONG_ACCOUNT_HISTORY("Ancienneté de compte > 12 mois", 15),

    // ─── Événements négatifs ─────────────────────────────────────
    CREDIT_LATE_PAYMENT_1D("Retard de remboursement (1-7 jours)", -10),
    CREDIT_LATE_PAYMENT_8D("Retard de remboursement (8-30 jours)", -30),
    CREDIT_DEFAULT("Défaut de crédit (> 30 jours)", -100),
    DEBT_OVERDUE("Dette entre particuliers en retard", -25),
    DEBT_DEFAULTED("Dette entre particuliers non honorée", -50),
    KYC_REJECTED("KYC rejeté", -20),
    FRAUD_SUSPECTED("Activité frauduleuse suspectée", -150),
    ACCOUNT_SUSPENDED("Compte suspendu", -50);

    private final String label;
    private final int defaultImpact;

    ScoreEventType(String label, int defaultImpact) {
        this.label = label;
        this.defaultImpact = defaultImpact;
    }

    public String getLabel() { return label; }
    public int getDefaultImpact() { return defaultImpact; }

    /**
     * Vérifie si cet événement améliore le score.
     */
    public boolean isPositive() {
        return defaultImpact > 0;
    }
}
