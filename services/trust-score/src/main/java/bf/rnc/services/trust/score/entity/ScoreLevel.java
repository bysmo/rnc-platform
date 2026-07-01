package bf.rnc.services.trust.score.entity;

/**
 * Niveau de score sur l'échelle 0-1000.
 *
 * <p>Correspondance avec les décisions crédit :</p>
 * <ul>
 *   <li>EXCELLENT (800-1000) : crédit automatique jusqu'à 500k XOF</li>
 *   <li>GOOD (650-799)       : crédit automatique jusqu'à 200k XOF</li>
 *   <li>FAIR (500-649)       : crédit avec analyse manuelle</li>
 *   <li>LOW (300-499)        : crédit refusé, micro-montants uniquement</li>
 *   <li>CRITICAL (0-299)     : aucun crédit, dispositif de redressement</li>
 * </ul>
 */
public enum ScoreLevel {
    CRITICAL(0, 299),
    LOW(300, 499),
    FAIR(500, 649),
    GOOD(650, 799),
    EXCELLENT(800, 1000);

    private final int min;
    private final int max;

    ScoreLevel(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public int getMin() { return min; }
    public int getMax() { return max; }

    /**
     * Détermine le niveau à partir d'une valeur de score.
     */
    public static ScoreLevel fromScore(int score) {
        if (score < 0) return CRITICAL;
        for (ScoreLevel level : values()) {
            if (score >= level.min && score <= level.max) {
                return level;
            }
        }
        return EXCELLENT;
    }

    /**
     * Vérifie si ce niveau autorise un crédit automatique (sans analyse manuelle).
     */
    public boolean allowsAutomaticCredit() {
        return this == EXCELLENT || this == GOOD;
    }

    /**
     * Vérifie si ce niveau bloque tout crédit.
     */
    public boolean blocksCredit() {
        return this == CRITICAL;
    }
}
