package bf.rnc.services.trust.school.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import bf.rnc.services.trust.school.service.TrustSchoolServiceService;

/**
 * REST controller — endpoints métier du microservice trust-school.
 */
@RestController
@RequestMapping("/api/v1/school")
@RequiredArgsConstructor
public class TrustSchoolServiceController {

    private final TrustSchoolServiceService service;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"service\":\"trust-school\",\"status\":\"UP\"}");
    }
}
