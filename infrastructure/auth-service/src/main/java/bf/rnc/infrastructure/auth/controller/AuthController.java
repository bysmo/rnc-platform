package bf.rnc.infrastructure.auth.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints d'authentification RNC.
 * Délègue à Keycloak via Admin Client pour la gestion des utilisateurs.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> request) {
        log.info("Registration request for: {}", request.get("phoneNumber"));
        // TODO: Create Keycloak user, send OTP SMS verification
        return ResponseEntity.ok(Map.of("status", "OTP_SENT", "message", "Code OTP envoyé par SMS"));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyOtp(@RequestBody Map<String, Object> request) {
        log.info("OTP verification for: {}", request.get("phoneNumber"));
        // TODO: Validate OTP, activate Keycloak user
        return ResponseEntity.ok(Map.of("status", "VERIFIED", "message", "Compte activé"));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, Object> request) {
        // TODO: Proxy to Keycloak token endpoint
        return ResponseEntity.ok(Map.of("status", "AUTHENTICATED"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of("status", "REFRESHED"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String auth) {
        // TODO: Revoke token in Keycloak
        return ResponseEntity.noContent().build();
    }
}
