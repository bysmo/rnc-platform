package bf.rnc.services.trust.qr.entity;

import bf.rnc.common.lib.audit.BaseAuditFields;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Autorisation préalable d'un marchand pour un citoyen (plafond de crédit).
 *
 * <p>Permet à un citoyen de bénéficier d'un crédit automatique chez un marchand
 * sans nouvelle validation, dans la limite du plafond accordé.</p>
 */
@Entity
@Table(name = "merchant_authorizations", schema = "trust_qr")
@Getter
@Setter
public class MerchantAuthorization extends BaseAuditFields {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "merchant_id", nullable = false, length = 100)
    private String merchantId;

    @Column(name = "citizen_id", nullable = false, length = 100)
    private String citizenId;

    @Column(name = "max_amount_minor", nullable = false)
    private Long maxAmountMinor;

    @Column(name = "used_amount_minor", nullable = false)
    private Long usedAmountMinor = 0L;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 500)
    private String revokedReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    // ============================================================
    // Helpers
    // ============================================================

    public long getAvailableAmount() {
        return maxAmountMinor - usedAmountMinor;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean canAuthorize(long amount) {
        return "ACTIVE".equals(status) && !isExpired() && getAvailableAmount() >= amount;
    }

    public void useAmount(long amount) {
        this.usedAmountMinor += amount;
        if (this.usedAmountMinor >= this.maxAmountMinor) {
            this.status = "EXHAUSTED";
        }
    }
}
