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
 * QR Code Confiance — émis par un marchand agréé.
 *
 * <p>Deux types :</p>
 * <ul>
 *   <li><b>STATIC_MERCHANT</b> : affiché en magasin, réutilisable</li>
 *   <li><b>DYNAMIC_TRANSACTION</b> : généré pour une transaction précise, usage unique</li>
 * </ul>
 *
 * <p>Le payload est chiffré et signé (ECDSA) pour empêcher la contrefaçon.</p>
 */
@Entity
@Table(name = "qr_codes", schema = "trust_qr")
@Getter
@Setter
public class QrCode extends BaseAuditFields {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "qr_reference", nullable = false, unique = true, length = 50)
    private String qrReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "qr_type", nullable = false, length = 20)
    private QrType qrType;

    @Column(name = "merchant_id", nullable = false, length = 100)
    private String merchantId;

    @Column(name = "service_point_id", length = 100)
    private String servicePointId;

    /** Pour QR dynamique : citoyen cible */
    @Column(name = "citizen_id", length = 100)
    private String citizenId;

    @Column(name = "payload_encrypted", nullable = false)
    private String payloadEncrypted;

    @Column(name = "payload_hash", nullable = false, length = 128)
    private String payloadHash;

    @Column(name = "signature", nullable = false, length = 500)
    private String signature;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "image_base64", columnDefinition = "TEXT")
    private String imageBase64;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private QrStatus status = QrStatus.ACTIVE;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "use_count", nullable = false)
    private Integer useCount = 0;

    @Column(name = "max_amount_minor")
    private Long maxAmountMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private QrCategory category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    // ============================================================
    // Helpers
    // ============================================================

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean canBeUsed() {
        if (status != QrStatus.ACTIVE) return false;
        if (isExpired()) return false;
        if (maxUses != null && useCount >= maxUses) return false;
        return true;
    }

    public void incrementUse() {
        this.useCount++;
        if (maxUses != null && this.useCount >= maxUses) {
            this.status = QrStatus.USED;
        }
    }
}
