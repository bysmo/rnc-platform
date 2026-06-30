package bf.rnc.services.trust.insurance.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import bf.rnc.services.trust.insurance.service.TrustInsuranceServiceService;

/**
 * REST controller — endpoints métier du microservice trust-insurance.
 */
@RestController
@RequestMapping("/api/v1/insurance")
@RequiredArgsConstructor
public class TrustInsuranceServiceController {

    private final TrustInsuranceServiceService service;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"service\":\"trust-insurance\",\"status\":\"UP\"}");
    }
}
