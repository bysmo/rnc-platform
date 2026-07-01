package bf.rnc.services.trust.qr.service;

import bf.rnc.common.lib.exception.BusinessException;
import bf.rnc.common.lib.exception.NotFoundException;
import bf.rnc.common.lib.util.IdGenerator;
import bf.rnc.common.security.audit.Auditable;
import bf.rnc.services.trust.qr.dto.*;
import bf.rnc.services.trust.qr.entity.*;
import bf.rnc.services.trust.qr.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service métier central — Trust QR : génération, scan, autorisation.
 *
 * <p>Deux types de QR Codes :</p>
 * <ul>
 *   <li><b>STATIC_MERCHANT</b> : affiché en magasin, réutilisable. Plafond par transaction.</li>
 *   <li><b>DYNAMIC_TRANSACTION</b> : généré pour une transaction, usage unique.</li>
 * </ul>
 *
 * <p>Le flux de scan est :</p>
 * <ol>
 *   <li>Citoyen scanne le QR → vérification signature + validité</li>
 *   <li>Création d'un enregistrement {@link QrScan} (status=SCANNED)</li>
 *   <li>Vérifications business (plafond, autorisation marchand, score citoyen)</li>
 *   <li>Autorisation (status=AUTHORIZED) → déclenchement crédit/escrow par d'autres services</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QrCodeService {

    private static final long DEFAULT_QR_TTL_SECONDS = 300; // 5 minutes pour QR dynamique
    private static final long DEFAULT_STATIC_MAX_AMOUNT = 200_000L; // 200k XOF par transaction

    private final QrCodeRepository qrCodeRepository;
    private final QrScanRepository qrScanRepository;
    private final MerchantAuthorizationRepository authzRepository;
    private final QrImageGenerator imageGenerator;
    private final QrSignatureService signatureService;
    private final ObjectMapper objectMapper;

    // ============================================================
    // GÉNÉRATION
    // ============================================================

    @Auditable(action = "GENERATE_QR", resourceType = "QrCode", actorType = "MERCHANT", extractResourceId = true)
    @Transactional
    public QrCodeResponse generateQrCode(QrGenerationRequest request) {
        // Construire le payload
        Map<String, Object> payloadData = new HashMap<>();
        payloadData.put("qrRef", IdGenerator.prefixed("QR", 12));
        payloadData.put("type", request.getQrType().name());
        payloadData.put("merchantId", request.getMerchantId());
        payloadData.put("category", request.getCategory().name());
        payloadData.put("nonce", signatureService.generateNonce(16));
        payloadData.put("timestamp", Instant.now().toEpochMilli());

        if (request.getServicePointId() != null) {
            payloadData.put("servicePointId", request.getServicePointId());
        }
        if (request.getCitizenId() != null) {
            payloadData.put("citizenId", request.getCitizenId());
        }
        if (request.getAmountMinor() != null) {
            payloadData.put("amount", request.getAmountMinor());
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payloadData);
        } catch (Exception e) {
            throw new RuntimeException("Erreur sérialisation payload", e);
        }

        // Chiffrer + signer
        String encryptedPayload = signatureService.encrypt(payloadJson);
        String payloadHash = signatureService.hash(payloadJson);
        String signature = signatureService.sign(payloadJson);

        // Générer l'image QR
        String imageBase64 = imageGenerator.generateBase64(payloadJson);

        // Créer l'entité
        QrCode qrCode = new QrCode();
        qrCode.setQrReference((String) payloadData.get("qrRef"));
        qrCode.setQrType(request.getQrType());
        qrCode.setMerchantId(request.getMerchantId());
        qrCode.setServicePointId(request.getServicePointId());
        qrCode.setCitizenId(request.getCitizenId());
        qrCode.setPayloadEncrypted(encryptedPayload);
        qrCode.setPayloadHash(payloadHash);
        qrCode.setSignature(signature);
        qrCode.setImageBase64(imageBase64);
        qrCode.setStatus(QrStatus.ACTIVE);
        qrCode.setCategory(request.getCategory());

        if (request.getQrType() == QrType.DYNAMIC_TRANSACTION) {
            qrCode.setExpiresAt(request.getExpiresAt() != null
                    ? request.getExpiresAt()
                    : Instant.now().plusSeconds(DEFAULT_QR_TTL_SECONDS));
            qrCode.setMaxUses(1);
            qrCode.setMaxAmountMinor(request.getAmountMinor());
        } else {
            // QR statique : pas d'expiration, max_uses illimité
            qrCode.setMaxUses(request.getMaxUses()); // null = illimité
            qrCode.setMaxAmountMinor(request.getMaxAmountMinor() != null
                    ? request.getMaxAmountMinor()
                    : DEFAULT_STATIC_MAX_AMOUNT);
        }

        qrCode = qrCodeRepository.save(qrCode);

        log.info("QR Code généré: ref={}, type={}, merchant={}, category={}",
                qrCode.getQrReference(), qrCode.getQrType(), qrCode.getMerchantId(), qrCode.getCategory());

        return toResponse(qrCode, payloadJson);
    }

    // ============================================================
    // SCAN
    // ============================================================

    @Auditable(action = "SCAN_QR", resourceType = "QrScan", actorType = "CITIZEN", extractResourceId = true)
    @Transactional
    public QrScanResponse scanQrCode(QrScanRequest request, String ipAddress, String userAgent) {
        long startTime = System.currentTimeMillis();

        // 1. Trouver le QR Code
        QrCode qrCode = qrCodeRepository.findByQrReferenceAndDeletedFalse(request.getQrReference())
                .orElseThrow(() -> new NotFoundException("QrCode", request.getQrReference()));

        // 2. Vérifier signature (anti-contrefaçon)
        // Pour le MVP, on vérifie juste que le payload hash correspond
        // (le payload envoyé par le scanner doit matcher celui stocké)
        if (!qrCode.getPayloadHash().equals(signatureService.hash(request.getScannedPayload()))) {
            log.warn("Signature QR invalide — ref={}", request.getQrReference());
            return createRejectedScan(qrCode, request, "Signature invalide — QR potentiellement contrefait",
                    ipAddress, userAgent, startTime);
        }

        // 3. Vérifier que le QR est utilisable
        if (!qrCode.canBeUsed()) {
            String reason = qrCode.isExpired() ? "QR Code expiré" : "QR Code non utilisable (statut: " + qrCode.getStatus() + ")";
            return createRejectedScan(qrCode, request, reason, ipAddress, userAgent, startTime);
        }

        // 4. Vérifier le montant
        if (qrCode.getMaxAmountMinor() != null && request.getAmountMinor() > qrCode.getMaxAmountMinor()) {
            return createRejectedScan(qrCode, request,
                    "Montant " + request.getAmountMinor() + " supérieur au plafond " + qrCode.getMaxAmountMinor(),
                    ipAddress, userAgent, startTime);
        }

        // 5. Vérifier autorisation marchand (si elle existe)
        MerchantAuthorization authz = authzRepository.findActiveAuthorization(
                qrCode.getMerchantId(), request.getCitizenId()).orElse(null);

        if (authz != null && !authz.canAuthorize(request.getAmountMinor())) {
            return createRejectedScan(qrCode, request,
                    "Autorisation marchand insuffisante (disponible: " + authz.getAvailableAmount() + ")",
                    ipAddress, userAgent, startTime);
        }

        // 6. Créer le scan autorisé
        QrScan scan = new QrScan();
        scan.setQrCodeId(qrCode.getId());
        scan.setCitizenId(request.getCitizenId());
        scan.setScanReference(IdGenerator.prefixed("SCAN", 12));
        scan.setScannedAt(Instant.now());
        scan.setIpAddress(ipAddress);
        scan.setUserAgent(userAgent);
        scan.setAmountMinor(request.getAmountMinor());
        scan.setStatus(ScanStatus.AUTHORIZED);
        scan.setAuthorizedAt(Instant.now());

        if (request.getLatitude() != null && request.getLongitude() != null) {
            try {
                scan.setGeolocation(objectMapper.writeValueAsString(Map.of(
                        "lat", request.getLatitude(),
                        "lng", request.getLongitude()
                )));
            } catch (Exception ignored) {}
        }

        scan = qrScanRepository.save(scan);

        // 7. Incrémenter l'usage du QR
        qrCode.incrementUse();
        qrCodeRepository.save(qrCode);

        // 8. Consommer l'autorisation
        if (authz != null) {
            authz.useAmount(request.getAmountMinor());
            authzRepository.save(authz);
        }

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("QR autorisé: scanRef={}, qrRef={}, citizen={}, amount={} XOF ({} ms)",
                scan.getScanReference(), qrCode.getQrReference(), request.getCitizenId(),
                request.getAmountMinor(), durationMs);

        return QrScanResponse.builder()
                .scanId(scan.getId())
                .scanReference(scan.getScanReference())
                .status(ScanStatus.AUTHORIZED)
                .merchantId(qrCode.getMerchantId())
                .qrCode(toResponse(qrCode, null))
                .amountMinor(request.getAmountMinor())
                .currency("XOF")
                .scannedAt(scan.getScannedAt())
                .authorizedAt(scan.getAuthorizedAt())
                .authorizationTimeMs(durationMs)
                .build();
    }

    // ============================================================
    // CONSULTATION
    // ============================================================

    @Transactional(readOnly = true)
    public QrCodeResponse getQrCode(String qrReference) {
        QrCode qrCode = qrCodeRepository.findByQrReferenceAndDeletedFalse(qrReference)
                .orElseThrow(() -> new NotFoundException("QrCode", qrReference));
        return toResponse(qrCode, null);
    }

    @Transactional(readOnly = true)
    public long countScans(UUID qrCodeId) {
        return qrScanRepository.countByQrCodeId(qrCodeId);
    }

    // ============================================================
    // ADMIN
    // ============================================================

    @Auditable(action = "REVOKE_QR", resourceType = "QrCode", actorType = "ADMIN")
    @Transactional
    public void revokeQrCode(String qrReference, String reason) {
        QrCode qrCode = qrCodeRepository.findByQrReferenceAndDeletedFalse(qrReference)
                .orElseThrow(() -> new NotFoundException("QrCode", qrReference));

        qrCode.setStatus(QrStatus.REVOKED);
        qrCodeRepository.save(qrCode);

        log.warn("QR révoqué: ref={} — raison: {}", qrReference, reason);
    }

    @Auditable(action = "GRANT_AUTHORIZATION", resourceType = "MerchantAuthorization", actorType = "ADMIN")
    @Transactional
    public MerchantAuthorization grantAuthorization(String merchantId, String citizenId,
                                                     long maxAmountMinor, Instant expiresAt) {
        // Vérifier qu'il n'y a pas déjà une autorisation active
        authzRepository.findActiveAuthorization(merchantId, citizenId).ifPresent(existing -> {
            existing.setStatus("REVOKED");
            existing.setRevokedAt(Instant.now());
            existing.setRevokedReason("Remplacée par nouvelle autorisation");
            authzRepository.save(existing);
        });

        MerchantAuthorization authz = new MerchantAuthorization();
        authz.setMerchantId(merchantId);
        authz.setCitizenId(citizenId);
        authz.setMaxAmountMinor(maxAmountMinor);
        authz.setExpiresAt(expiresAt);
        return authzRepository.save(authz);
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private QrScanResponse createRejectedScan(QrCode qrCode, QrScanRequest request,
                                                String reason, String ip, String userAgent,
                                                long startTime) {
        QrScan scan = new QrScan();
        scan.setQrCodeId(qrCode.getId());
        scan.setCitizenId(request.getCitizenId());
        scan.setScanReference(IdGenerator.prefixed("SCAN", 12));
        scan.setScannedAt(Instant.now());
        scan.setIpAddress(ip);
        scan.setUserAgent(userAgent);
        scan.setAmountMinor(request.getAmountMinor());
        scan.setStatus(ScanStatus.REJECTED);
        scan.setRejectionReason(reason);

        qrScanRepository.save(scan);

        long durationMs = System.currentTimeMillis() - startTime;
        log.warn("QR rejeté: qrRef={}, citizen={}, raison: {} ({} ms)",
                qrCode.getQrReference(), request.getCitizenId(), reason, durationMs);

        return QrScanResponse.builder()
                .scanId(scan.getId())
                .scanReference(scan.getScanReference())
                .status(ScanStatus.REJECTED)
                .merchantId(qrCode.getMerchantId())
                .qrCode(toResponse(qrCode, null))
                .amountMinor(request.getAmountMinor())
                .currency("XOF")
                .rejectionReason(reason)
                .scannedAt(scan.getScannedAt())
                .authorizationTimeMs(durationMs)
                .build();
    }

    private QrCodeResponse toResponse(QrCode qrCode, String payloadDecoded) {
        return QrCodeResponse.builder()
                .id(qrCode.getId())
                .qrReference(qrCode.getQrReference())
                .qrType(qrCode.getQrType())
                .merchantId(qrCode.getMerchantId())
                .servicePointId(qrCode.getServicePointId())
                .category(qrCode.getCategory())
                .status(qrCode.getStatus())
                .expiresAt(qrCode.getExpiresAt())
                .maxUses(qrCode.getMaxUses())
                .useCount(qrCode.getUseCount())
                .maxAmountMinor(qrCode.getMaxAmountMinor())
                .createdAt(qrCode.getCreatedAt())
                .imageBase64(qrCode.getImageBase64())
                .imageUrl(qrCode.getImageUrl())
                .payloadDecoded(payloadDecoded)
                .build();
    }
}
