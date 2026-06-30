package bf.rnc.services.trust.collect.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import bf.rnc.services.trust.collect.service.TrustCollectServiceService;

/**
 * REST controller — endpoints métier du microservice trust-collect.
 */
@RestController
@RequestMapping("/api/v1/collect")
@RequiredArgsConstructor
public class TrustCollectServiceController {

    private final TrustCollectServiceService service;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"service\":\"trust-collect\",\"status\":\"UP\"}");
    }
}
