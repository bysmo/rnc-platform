package bf.rnc.services.trust.qr.controller;

import bf.rnc.common.security.SecurityContextHelper;
import bf.rnc.services.trust.qr.dto.QrCodeResponse;
import bf.rnc.services.trust.qr.dto.QrGenerationRequest;
import bf.rnc.services.trust.qr.dto.QrScanRequest;
import bf.rnc.services.trust.qr.dto.QrScanResponse;
import bf.rnc.services.trust.qr.service.QrCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * API REST — Trust QR : QR Code Confiance.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/qr")
@RequiredArgsConstructor
@Tag(name = "Trust QR", description = "QR Code Confiance — génération, scan, autorisation")
public class QrCodeController {

    private final QrCodeService qrCodeService;
    private final SecurityContextHelper securityContextHelper;

    // ============================================================
    // GÉNÉRATION
    // ============================================================

    @PostMapping("/generate")
    @Operation(summary = "Générer un QR Code (marchand)")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ResponseEntity<QrCodeResponse> generate(@Valid @RequestBody QrGenerationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(qrCodeService.generateQrCode(request));
    }

    // ============================================================
    // SCAN
    // ============================================================

    @PostMapping("/scan")
    @Operation(summary = "Scanner un QR Code (citoyen)")
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<QrScanResponse> scan(
            @Valid @RequestBody QrScanRequest request,
            HttpServletRequest httpRequest) {

        String ip = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        return ResponseEntity.ok(qrCodeService.scanQrCode(request, ip, userAgent));
    }

    // ============================================================
    // CONSULTATION
    // ============================================================

    @GetMapping("/{qrReference}")
    @Operation(summary = "Consulter un QR Code")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN', 'AUDITOR')")
    public ResponseEntity<QrCodeResponse> getQrCode(@PathVariable String qrReference) {
        return ResponseEntity.ok(qrCodeService.getQrCode(qrReference));
    }

    // ============================================================
    // ADMIN
    // ============================================================

    @PostMapping("/{qrReference}/revoke")
    @Operation(summary = "Révoquer un QR Code")
    @PreAuthorize("hasAnyRole('ADMIN', 'MERCHANT')")
    public ResponseEntity<Void> revoke(
            @PathVariable String qrReference,
            @RequestParam String reason) {
        qrCodeService.revokeQrCode(qrReference, reason);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/authorizations/grant")
    @Operation(summary = "Accorder une autorisation marchand-citoyen")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> grantAuthorization(
            @RequestParam String merchantId,
            @RequestParam String citizenId,
            @RequestParam Long maxAmountMinor,
            @RequestParam(required = false) Instant expiresAt) {
        var authz = qrCodeService.grantAuthorization(merchantId, citizenId, maxAmountMinor, expiresAt);
        return ResponseEntity.ok(Map.of(
                "id", authz.getId(),
                "status", authz.getStatus(),
                "maxAmountMinor", authz.getMaxAmountMinor()
        ));
    }

    // ============================================================
    // HEALTH
    // ============================================================

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "service", "trust-qr",
                "status", "UP",
                "version", "0.1.0"
        ));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
