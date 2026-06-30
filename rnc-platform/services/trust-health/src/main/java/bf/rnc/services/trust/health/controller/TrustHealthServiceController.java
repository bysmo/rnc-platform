package bf.rnc.services.trust.health.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import bf.rnc.services.trust.health.service.TrustHealthServiceService;

/**
 * REST controller — endpoints métier du microservice trust-health.
 */
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class TrustHealthServiceController {

    private final TrustHealthServiceService service;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"service\":\"trust-health\",\"status\":\"UP\"}");
    }
}
