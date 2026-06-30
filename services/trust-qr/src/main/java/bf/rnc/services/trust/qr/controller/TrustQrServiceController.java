package bf.rnc.services.trust.qr.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import bf.rnc.services.trust.qr.service.TrustQrServiceService;

/**
 * REST controller — endpoints métier du microservice trust-qr.
 */
@RestController
@RequestMapping("/api/v1/qr")
@RequiredArgsConstructor
public class TrustQrServiceController {

    private final TrustQrServiceService service;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"service\":\"trust-qr\",\"status\":\"UP\"}");
    }
}
