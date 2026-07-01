package bf.rnc.services.trust.credit.controller;

import bf.rnc.common.lib.dto.PageResponse;
import bf.rnc.common.lib.exception.BusinessException;
import bf.rnc.common.security.SecurityContextHelper;
import bf.rnc.services.trust.credit.dto.*;
import bf.rnc.services.trust.credit.entity.Credit;
import bf.rnc.services.trust.credit.entity.CreditEvent;
import bf.rnc.services.trust.credit.entity.CreditStatus;
import bf.rnc.services.trust.credit.service.CreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API REST — Trust Credit : gestion des nano-crédits.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/credits")
@RequiredArgsConstructor
@Tag(name = "Trust Credit", description = "Nano-crédits instantanés RNC")
public class CreditController {

    private final CreditService creditService;
    private final SecurityContextHelper securityContextHelper;

    // ============================================================
    // DEMANDE
    // ============================================================

    @PostMapping
    @Operation(summary = "Demander un nano-crédit")
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<CreditResponse> requestCredit(@Valid @RequestBody CreditRequest request) {
        Credit credit = creditService.requestCredit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creditService.getCreditDetail(credit.getId()));
    }

    // ============================================================
    // DÉCISION (agent/admin)
    // ============================================================

    @PostMapping("/{id}/decision")
    @Operation(summary = "Approuver ou rejeter un crédit")
    @PreAuthorize("hasAnyRole('ADMIN', 'BANK_AGENT')")
    public ResponseEntity<CreditResponse> decide(
            @PathVariable UUID id,
            @Valid @RequestBody CreditDecisionRequest decision) {
        String actorId = securityContextHelper.getCurrentUserId();
        Credit credit = creditService.approve(id, decision, actorId);
        return ResponseEntity.ok(creditService.getCreditDetail(credit.getId()));
    }

    @PostMapping("/{id}/disburse")
    @Operation(summary = "Débloquer un crédit approuvé")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<CreditResponse> disburse(@PathVariable UUID id) {
        Credit credit = creditService.disburse(id);
        return ResponseEntity.ok(creditService.getCreditDetail(credit.getId()));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Annuler un crédit")
    @PreAuthorize("hasAnyRole('ADMIN', 'CITIZEN')")
    public ResponseEntity<CreditResponse> cancel(
            @PathVariable UUID id,
            @RequestParam String reason) {
        String actorId = securityContextHelper.getCurrentUserId();
        Credit credit = creditService.cancel(id, reason, actorId);
        return ResponseEntity.ok(creditService.getCreditDetail(credit.getId()));
    }

    // ============================================================
    // CONSULTATION
    // ============================================================

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un crédit")
    @PreAuthorize("hasAnyRole('CITIZEN', 'ADMIN', 'BANK_AGENT', 'AUDITOR')")
    public ResponseEntity<CreditResponse> getDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(creditService.getCreditDetail(id));
    }

    @GetMapping("/reference/{reference}")
    @Operation(summary = "Recherche par référence (CR-XXXX)")
    @PreAuthorize("hasAnyRole('ADMIN', 'BANK_AGENT', 'AUDITOR')")
    public ResponseEntity<CreditResponse> getByReference(@PathVariable String reference) {
        Credit credit = creditService.findCreditByReference(reference);
        return ResponseEntity.ok(creditService.getCreditDetail(credit.getId()));
    }

    @GetMapping("/citizen/{citizenId}")
    @Operation(summary = "Crédits d'un citoyen")
    @PreAuthorize("hasAnyRole('CITIZEN', 'ADMIN', 'BANK_AGENT', 'AUDITOR')")
    public ResponseEntity<PageResponse<CreditResponse>> getCitizenCredits(
            @PathVariable String citizenId,
            @ParameterObject Pageable pageable) {
        // TODO: ajouter une méthode service qui retourne directement des CreditResponse
        return ResponseEntity.ok(PageResponse.empty());
    }

    @GetMapping("/{id}/installments")
    @Operation(summary = "Échéancier d'un crédit")
    @PreAuthorize("hasAnyRole('CITIZEN', 'ADMIN', 'BANK_AGENT', 'AUDITOR')")
    public ResponseEntity<List<InstallmentResponse>> getInstallments(@PathVariable UUID id) {
        return ResponseEntity.ok(creditService.getInstallments(id));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Historique des événements d'un crédit (audit)")
    @PreAuthorize("hasAnyRole('ADMIN', 'BANK_AGENT', 'AUDITOR')")
    public ResponseEntity<List<CreditEvent>> getHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(creditService.getCreditHistory(id));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Crédits par statut (admin)")
    @PreAuthorize("hasAnyRole('ADMIN', 'BANK_AGENT', 'AUDITOR')")
    public ResponseEntity<PageResponse<CreditResponse>> getByStatus(
            @PathVariable CreditStatus status,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(PageResponse.empty());
    }

    // ============================================================
    // PAIEMENT
    // ============================================================

    @PostMapping("/payments")
    @Operation(summary = "Enregistrer un paiement (webhook Mobile Money ou manuel)")
    @PreAuthorize("hasAnyRole('SYSTEM', 'ADMIN', 'CITIZEN')")
    public ResponseEntity<Map<String, Object>> recordPayment(@Valid @RequestBody PaymentRequest request) {
        var payment = creditService.recordPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "paymentReference", payment.getPaymentReference(),
                "status", payment.getStatus().name(),
                "amount", payment.getAmountMinor()
        ));
    }

    // ============================================================
    // ADMIN / CRON
    // ============================================================

    @PostMapping("/admin/mark-defaulted")
    @Operation(summary = "Marquer les crédits en défaut (job planifié)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Integer>> markDefaulted() {
        int count = creditService.markDefaultedCredits();
        return ResponseEntity.ok(Map.of("markedDefaulted", count));
    }

    // ============================================================
    // HEALTH
    // ============================================================

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "service", "trust-credit",
                "status", "UP",
                "version", "0.1.0"
        ));
    }
}
