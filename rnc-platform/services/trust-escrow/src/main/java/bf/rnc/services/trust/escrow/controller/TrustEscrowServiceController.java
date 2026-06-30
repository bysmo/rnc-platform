package bf.rnc.services.trust.escrow.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import bf.rnc.services.trust.escrow.service.TrustEscrowServiceService;

/**
 * REST controller — endpoints métier du microservice trust-escrow.
 */
@RestController
@RequestMapping("/api/v1/escrow")
@RequiredArgsConstructor
public class TrustEscrowServiceController {

    private final TrustEscrowServiceService service;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"service\":\"trust-escrow\",\"status\":\"UP\"}");
    }
}
