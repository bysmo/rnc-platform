package bf.rnc.services.trust.qr.service;

import bf.rnc.common.lib.exception.NotFoundException;
import bf.rnc.services.trust.qr.dto.QrGenerationRequest;
import bf.rnc.services.trust.qr.dto.QrScanRequest;
import bf.rnc.services.trust.qr.dto.QrScanResponse;
import bf.rnc.services.trust.qr.entity.*;
import bf.rnc.services.trust.qr.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires du QrCodeService — logique métier isolée.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QrCodeService — Tests unitaires")
class QrCodeServiceTest {

    @Mock private QrCodeRepository qrCodeRepository;
    @Mock private QrScanRepository qrScanRepository;
    @Mock private MerchantAuthorizationRepository authzRepository;
    @Mock private QrImageGenerator imageGenerator;
    @Mock private QrSignatureService signatureService;

    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private QrCodeService qrCodeService;

    @BeforeEach
    void setUp() {
        when(imageGenerator.generateBase64(anyString())).thenReturn("iVBORbase64image");
        when(signatureService.encrypt(anyString())).thenReturn("encrypted");
        when(signatureService.hash(anyString())).thenReturn("hash-123");
        when(signatureService.sign(anyString())).thenReturn("signature-123");
        when(signatureService.generateNonce(anyInt())).thenReturn("nonce-123");
    }

    // ============================================================
    // GÉNÉRATION
    // ============================================================

    @Nested
    @DisplayName("generateQrCode()")
    class Generate {

        @Test
        @DisplayName("✓ Génération QR statique marchand")
        void shouldGenerateStaticMerchantQr() {
            QrGenerationRequest req = QrGenerationRequest.builder()
                    .qrType(QrType.STATIC_MERCHANT)
                    .merchantId("merchant-001")
                    .category(QrCategory.MERCHANT)
                    .build();

            when(qrCodeRepository.save(any())).thenAnswer(inv -> {
                QrCode q = inv.getArgument(0);
                q.setId(UUID.randomUUID());
                return q;
            });

            var result = qrCodeService.generateQrCode(req);

            assertThat(result.getQrReference()).startsWith("QR-");
            assertThat(result.getQrType()).isEqualTo(QrType.STATIC_MERCHANT);
            assertThat(result.getStatus()).isEqualTo(QrStatus.ACTIVE);
            assertThat(result.getImageBase64()).isNotNull();
            assertThat(result.getMaxAmountMinor()).isEqualTo(200_000L); // default
        }

        @Test
        @DisplayName("✓ Génération QR dynamique transaction — usage unique")
        void shouldGenerateDynamicTransactionQr() {
            QrGenerationRequest req = QrGenerationRequest.builder()
                    .qrType(QrType.DYNAMIC_TRANSACTION)
                    .merchantId("merchant-001")
                    .citizenId("citizen-001")
                    .category(QrCategory.HEALTH)
                    .amountMinor(50_000L)
                    .build();

            when(qrCodeRepository.save(any())).thenAnswer(inv -> {
                QrCode q = inv.getArgument(0);
                q.setId(UUID.randomUUID());
                return q;
            });

            var result = qrCodeService.generateQrCode(req);

            assertThat(result.getQrType()).isEqualTo(QrType.DYNAMIC_TRANSACTION);
            assertThat(result.getExpiresAt()).isNotNull(); // 5 min par défaut
            assertThat(result.getMaxUses()).isEqualTo(1);
        }
    }

    // ============================================================
    // SCAN
    // ============================================================

    @Nested
    @DisplayName("scanQrCode()")
    class Scan {

        @Test
        @DisplayName("✓ Scan réussi — autorisé")
        void shouldAuthorizeScanSuccessfully() {
            QrCode qrCode = buildQrCode(QrType.STATIC_MERCHANT, QrStatus.ACTIVE);
            String payloadJson = "test-payload";

            when(qrCodeRepository.findByQrReferenceAndDeletedFalse("QR-ABC"))
                    .thenReturn(Optional.of(qrCode));
            when(signatureService.hash(payloadJson)).thenReturn(qrCode.getPayloadHash());
            when(qrScanRepository.save(any())).thenAnswer(inv -> {
                QrScan s = inv.getArgument(0);
                s.setId(UUID.randomUUID());
                return s;
            });
            when(qrCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            QrScanRequest req = QrScanRequest.builder()
                    .qrReference("QR-ABC")
                    .scannedPayload(payloadJson)
                    .citizenId("citizen-001")
                    .amountMinor(30_000L)
                    .build();

            QrScanResponse result = qrCodeService.scanQrCode(req, "127.0.0.1", "JUnit");

            assertThat(result.getStatus()).isEqualTo(ScanStatus.AUTHORIZED);
            assertThat(result.getMerchantId()).isEqualTo("merchant-001");
            assertThat(result.getAuthorizationTimeMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("✗ Rejet si signature invalide (QR contrefait)")
        void shouldRejectInvalidSignature() {
            QrCode qrCode = buildQrCode(QrType.STATIC_MERCHANT, QrStatus.ACTIVE);

            when(qrCodeRepository.findByQrReferenceAndDeletedFalse("QR-ABC"))
                    .thenReturn(Optional.of(qrCode));
            when(signatureService.hash("bad-payload")).thenReturn("different-hash");

            QrScanRequest req = QrScanRequest.builder()
                    .qrReference("QR-ABC")
                    .scannedPayload("bad-payload")
                    .citizenId("citizen-001")
                    .amountMinor(10_000L)
                    .build();

            QrScanResponse result = qrCodeService.scanQrCode(req, "ip", "ua");

            assertThat(result.getStatus()).isEqualTo(ScanStatus.REJECTED);
            assertThat(result.getRejectionReason()).contains("Signature");
        }

        @Test
        @DisplayName("✗ Rejet si montant dépasse plafond")
        void shouldRejectAmountExceedingMax() {
            QrCode qrCode = buildQrCode(QrType.STATIC_MERCHANT, QrStatus.ACTIVE);
            qrCode.setMaxAmountMinor(20_000L);
            String payloadJson = "test-payload";

            when(qrCodeRepository.findByQrReferenceAndDeletedFalse("QR-ABC"))
                    .thenReturn(Optional.of(qrCode));
            when(signatureService.hash(payloadJson)).thenReturn(qrCode.getPayloadHash());

            QrScanRequest req = QrScanRequest.builder()
                    .qrReference("QR-ABC")
                    .scannedPayload(payloadJson)
                    .citizenId("citizen-001")
                    .amountMinor(50_000L) // dépasse 20k
                    .build();

            QrScanResponse result = qrCodeService.scanQrCode(req, "ip", "ua");

            assertThat(result.getStatus()).isEqualTo(ScanStatus.REJECTED);
            assertThat(result.getRejectionReason()).contains("plafond");
        }

        @Test
        @DisplayName("✗ NotFoundException si QR inexistant")
        void shouldThrowIfQrNotFound() {
            when(qrCodeRepository.findByQrReferenceAndDeletedFalse("UNKNOWN"))
                    .thenReturn(Optional.empty());

            QrScanRequest req = QrScanRequest.builder()
                    .qrReference("UNKNOWN")
                    .scannedPayload("x")
                    .citizenId("citizen-001")
                    .amountMinor(10_000L)
                    .build();

            assertThatThrownBy(() -> qrCodeService.scanQrCode(req, "ip", "ua"))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("✗ Rejet si QR expiré")
        void shouldRejectExpiredQr() {
            QrCode qrCode = buildQrCode(QrType.DYNAMIC_TRANSACTION, QrStatus.ACTIVE);
            qrCode.setExpiresAt(Instant.now().minusSeconds(60)); // expiré
            String payloadJson = "test-payload";

            when(qrCodeRepository.findByQrReferenceAndDeletedFalse("QR-ABC"))
                    .thenReturn(Optional.of(qrCode));
            when(signatureService.hash(payloadJson)).thenReturn(qrCode.getPayloadHash());

            QrScanRequest req = QrScanRequest.builder()
                    .qrReference("QR-ABC")
                    .scannedPayload(payloadJson)
                    .citizenId("citizen-001")
                    .amountMinor(10_000L)
                    .build();

            QrScanResponse result = qrCodeService.scanQrCode(req, "ip", "ua");

            assertThat(result.getStatus()).isEqualTo(ScanStatus.REJECTED);
            assertThat(result.getRejectionReason()).contains("expiré");
        }
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private QrCode buildQrCode(QrType type, QrStatus status) {
        QrCode q = new QrCode();
        q.setId(UUID.randomUUID());
        q.setQrReference("QR-ABC");
        q.setQrType(type);
        q.setMerchantId("merchant-001");
        q.setPayloadEncrypted("encrypted");
        q.setPayloadHash("hash-123");
        q.setSignature("signature-123");
        q.setStatus(status);
        q.setUseCount(0);
        q.setCategory(QrCategory.MERCHANT);
        q.setMaxAmountMinor(200_000L);
        return q;
    }
}
