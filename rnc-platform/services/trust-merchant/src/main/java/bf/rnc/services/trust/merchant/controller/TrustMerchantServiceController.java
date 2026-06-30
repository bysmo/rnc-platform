package bf.rnc.services.trust.merchant.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import bf.rnc.services.trust.merchant.service.TrustMerchantServiceService;

/**
 * REST controller — endpoints métier du microservice trust-merchant.
 */
@RestController
@RequestMapping("/api/v1/merchant")
@RequiredArgsConstructor
public class TrustMerchantServiceController {

    private final TrustMerchantServiceService service;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"service\":\"trust-merchant\",\"status\":\"UP\"}");
    }
}
