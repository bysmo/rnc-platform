package bf.rnc.services.trust.qr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Demande de scan d'un QR Code par un citoyen.
 */
@Getter
@Setter
@Builder
public class QrScanRequest {

    /** Référence du QR Code scanné (QR-XXXX...) */
    @NotBlank
    private String qrReference;

    /** Payload lu par le scanner (contenu chiffré) */
    @NotBlank
    private String scannedPayload;

    @NotBlank
    private String citizenId;

    /** Montant demandé par le citoyen (saisi après scan) */
    @NotNull
    @Positive
    private Long amountMinor;

    /** Latitude (optionnel — pour prévention fraude) */
    private Double latitude;

    /** Longitude (optionnel) */
    private Double longitude;
}
