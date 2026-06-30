package bf.rnc.services.trust.credit.controller;

import bf.rnc.common.lib.util.Money;
import bf.rnc.services.trust.credit.entity.Credit;
import bf.rnc.services.trust.credit.service.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST API — Gestion des nano-crédits.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/credits")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;

    @PostMapping
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<Credit> requestCredit(@RequestBody Map<String, Object> request) {
        Money amount = Money.xof(Long.parseLong(request.get("amount").toString()));
        String citizenId = request.get("citizenId").toString();
        String purpose = request.get("purpose").toString();
        Integer durationDays = Integer.parseInt(request.get("durationDays").toString());
        Integer rateBps = Integer.parseInt(request.getOrDefault("interestRateBps", "0").toString());

        return ResponseEntity.ok(creditService.requestCredit(citizenId, amount, purpose, durationDays, rateBps));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Credit> approve(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String escrowId = body.get("escrowAccountId").toString();
        Integer trustScore = Integer.parseInt(body.get("trustScore").toString());
        return ResponseEntity.ok(creditService.approve(id, escrowId, trustScore));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Credit> reject(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(creditService.reject(id, body.get("reason")));
    }

    @PostMapping("/{id}/disburse")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Credit> disburse(@PathVariable UUID id) {
        return ResponseEntity.ok(creditService.disburse(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CITIZEN', 'ADMIN', 'AUDITOR')")
    public ResponseEntity<Credit> get(@PathVariable UUID id) {
        return ResponseEntity.of(creditService.creditRepository().findById(id));
    }
}
