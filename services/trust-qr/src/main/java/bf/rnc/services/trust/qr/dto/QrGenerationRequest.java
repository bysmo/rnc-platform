package bf.rnc.services.trust.qr.dto;

import bf.rnc.services.trust.qr.entity.QrCategory;
import bf.rnc.services.trust.qr.entity.QrType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Demande de génération d'un QR Code.
 */
@Getter
@Setter
@Builder
public class QrGenerationRequest {

    @NotNull
    private QrType qrType;

    @NotBlank
    private String merchantId;

    private String servicePointId;

    /** Pour QR dynamique uniquement : citoyen cible */
    private String citizenId;

    @NotNull
    private QrCategory category;

    /** Pour QR dynamique : montant exact. Pour QR statique : plafond */
    @Positive
    private Long amountMinor;

    /** Pour QR dynamique : date d'expiration */
    private Instant expiresAt;

    /** Pour QR statique : nombre max d'utilisations (null = illimité) */
    private Integer maxUses;

    /** Pour QR statique : plafond par transaction */
    @Positive
    private Long maxAmountMinor;
}
