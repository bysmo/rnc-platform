package bf.rnc.services.trust.qr.dto;

import bf.rnc.services.trust.qr.entity.ScanStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Résultat d'un scan de QR Code.
 */
@Getter
@Builder
public class QrScanResponse {

    private UUID scanId;
    private String scanReference;
    private ScanStatus status;
    private String merchantId;
    private String merchantName;
    private QrCodeResponse qrCode;
    private Long amountMinor;
    private String currency;
    private String rejectionReason;
    private String creditId;       // si crédit déclenché
    private String escrowAccountId;
    private Instant scannedAt;
    private Instant authorizedAt;

    /** Délai d'autorisation en ms (pour observabilité) */
    private Long authorizationTimeMs;
}
