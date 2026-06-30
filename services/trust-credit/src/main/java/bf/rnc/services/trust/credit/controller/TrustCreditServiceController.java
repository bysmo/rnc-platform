package bf.rnc.services.trust.credit.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import bf.rnc.services.trust.credit.service.TrustCreditServiceService;

/**
 * REST controller — endpoints métier du microservice trust-credit.
 */
@RestController
@RequestMapping("/api/v1/credit")
@RequiredArgsConstructor
public class TrustCreditServiceController {

    private final TrustCreditServiceService service;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"service\":\"trust-credit\",\"status\":\"UP\"}");
    }
}
