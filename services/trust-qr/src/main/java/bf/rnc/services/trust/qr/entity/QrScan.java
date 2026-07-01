package bf.rnc.services.trust.qr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Scan d'un QR Code par un citoyen (audit immuable).
 */
@Entity
@Table(name = "qr_scans", schema = "trust_qr")
@Getter
@Setter
public class QrScan {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "qr_code_id", nullable = false)
    private UUID qrCodeId;

    @Column(name = "citizen_id", nullable = false, length = 100)
    private String citizenId;

    @Column(name = "scan_reference", nullable = false, unique = true, length = 50)
    private String scanReference;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt = Instant.now();

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "geolocation", columnDefinition = "jsonb")
    private String geolocation;

    @Column(name = "amount_minor")
    private Long amountMinor;

    @Column(name = "currency", length = 3)
    private String currency = "XOF";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScanStatus status = ScanStatus.SCANNED;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "credit_id", length = 100)
    private String creditId;

    @Column(name = "escrow_account_id", length = 100)
    private String escrowAccountId;

    @Column(name = "authorized_at")
    private Instant authorizedAt;

    @Column(name = "authorized_by", length = 100)
    private String authorizedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
