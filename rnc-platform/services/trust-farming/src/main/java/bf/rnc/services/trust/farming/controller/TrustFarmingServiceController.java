package bf.rnc.services.trust.farming.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import bf.rnc.services.trust.farming.service.TrustFarmingServiceService;

/**
 * REST controller — endpoints métier du microservice trust-farming.
 */
@RestController
@RequestMapping("/api/v1/farming")
@RequiredArgsConstructor
public class TrustFarmingServiceController {

    private final TrustFarmingServiceService service;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"service\":\"trust-farming\",\"status\":\"UP\"}");
    }
}
