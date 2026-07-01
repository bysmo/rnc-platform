package bf.rnc.services.trust.qr.dto;

import bf.rnc.services.trust.qr.entity.QrCategory;
import bf.rnc.services.trust.qr.entity.QrStatus;
import bf.rnc.services.trust.qr.entity.QrType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Réponse API — QR Code généré.
 */
@Getter
@Builder
public class QrCodeResponse {

    private UUID id;
    private String qrReference;
    private QrType qrType;
    private String merchantId;
    private String servicePointId;
    private QrCategory category;
    private QrStatus status;
    private Instant expiresAt;
    private Integer maxUses;
    private Integer useCount;
    private Long maxAmountMinor;
    private Instant createdAt;

    /** Image PNG en Base64 — à afficher ou imprimer */
    private String imageBase64;

    /** URL publique du QR (optionnel) */
    private String imageUrl;

    /** Payload déchiffré (pour debug admin) */
    private String payloadDecoded;
}
