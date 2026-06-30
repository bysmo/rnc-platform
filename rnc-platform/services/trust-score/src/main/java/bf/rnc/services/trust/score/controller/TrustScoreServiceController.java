package bf.rnc.services.trust.score.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import bf.rnc.services.trust.score.service.TrustScoreServiceService;

/**
 * REST controller — endpoints métier du microservice trust-score.
 */
@RestController
@RequestMapping("/api/v1/score")
@RequiredArgsConstructor
public class TrustScoreServiceController {

    private final TrustScoreServiceService service;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"service\":\"trust-score\",\"status\":\"UP\"}");
    }
}
