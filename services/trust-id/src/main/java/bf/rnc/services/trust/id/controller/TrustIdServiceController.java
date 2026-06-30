package bf.rnc.services.trust.id.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import bf.rnc.services.trust.id.service.TrustIdServiceService;

/**
 * REST controller — endpoints métier du microservice trust-id.
 */
@RestController
@RequestMapping("/api/v1/id")
@RequiredArgsConstructor
public class TrustIdServiceController {

    private final TrustIdServiceService service;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"service\":\"trust-id\",\"status\":\"UP\"}");
    }
}
