package bf.rnc.common.lib.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value object pour les montants financiers.
 * Utilise BigDecimal pour éviter les erreurs de précision (critique pour les nano-crédits).
 * Devise par défaut: XOF (Franc CFA — UEMOA).
 */
public record Money(BigDecimal amount, String currency) {

    public static final String XOF = "XOF";

    public Money {
        Objects.requireNonNull(amount, "Le montant ne peut pas être null");
        Objects.requireNonNull(currency, "La devise ne peut pas être null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le montant ne peut pas être négatif");
        }
        amount = amount.setScale(0, RoundingMode.HALF_UP); // XOF n'a pas de centimes
        currency = currency.toUpperCase();
    }

    public static Money xof(long amount) {
        return new Money(BigDecimal.valueOf(amount), XOF);
    }

    public static Money xof(BigDecimal amount) {
        return new Money(amount, XOF);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money multiply(int factor) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(factor)), this.currency);
    }

    public Money percentage(BigDecimal pct) {
        return new Money(this.amount.multiply(pct).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP), this.currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isZero() {
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                "Devises incompatibles: " + this.currency + " vs " + other.currency);
        }
    }
}
