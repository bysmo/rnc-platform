package bf.rnc.services.trust.debt.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import bf.rnc.services.trust.debt.service.TrustDebtServiceService;

/**
 * REST controller — endpoints métier du microservice trust-debt.
 */
@RestController
@RequestMapping("/api/v1/debt")
@RequiredArgsConstructor
public class TrustDebtServiceController {

    private final TrustDebtServiceService service;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"service\":\"trust-debt\",\"status\":\"UP\"}");
    }
}
