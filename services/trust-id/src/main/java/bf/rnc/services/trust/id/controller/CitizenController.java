package bf.rnc.services.trust.id.controller;

import bf.rnc.common.lib.dto.PageResponse;
import bf.rnc.common.lib.exception.BusinessException;
import bf.rnc.common.security.SecurityContextHelper;
import bf.rnc.services.trust.id.dto.*;
import bf.rnc.services.trust.id.entity.AccountStatus;
import bf.rnc.services.trust.id.entity.Citizen;
import bf.rnc.services.trust.id.entity.KycStatus;
import bf.rnc.services.trust.id.repository.CitizenRepository;
import bf.rnc.services.trust.id.service.CitizenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * API REST — Trust ID : gestion de l'identité financière des citoyens.
 *
 * <p>Tous les endpoints sont préfixés par <code>/api/v1/identity</code>.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/identity")
@RequiredArgsConstructor
@Tag(name = "Trust ID", description = "Identité financière numérique des citoyens RNC")
public class CitizenController {

    private final CitizenService citizenService;
    private final CitizenRepository citizenRepository;
    private final SecurityContextHelper securityContextHelper;

    // ============================================================
    // INSCRIPTION
    // ============================================================

    @PostMapping("/register")
    @Operation(summary = "Inscription d'un citoyen", description = "Crée un compte citoyen et envoie un OTP SMS")
    public ResponseEntity<RegistrationResponse> register(
            @Valid @RequestBody RegistrationRequest request,
            HttpServletRequest httpRequest) {

        String ip = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        RegistrationResponse response = citizenService.register(request, ip, userAgent);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ============================================================
    // OTP
    // ============================================================

    @PostMapping("/verify-otp")
    @Operation(summary = "Vérifier le code OTP", description = "Active le compte après vérification OTP")
    public ResponseEntity<CitizenProfileResponse> verifyOtp(
            @Valid @RequestBody OtpVerificationRequest request,
            HttpServletRequest httpRequest) {

        String ip = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        return ResponseEntity.ok(citizenService.verifyOtp(request, ip, userAgent));
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Renvoyer un OTP")
    public ResponseEntity<Map<String, String>> resendOtp(
            @RequestParam String citizenReference,
            HttpServletRequest httpRequest) {

        citizenService.resendOtp(citizenReference,
                extractClientIp(httpRequest), httpRequest.getHeader("User-Agent"));

        return ResponseEntity.ok(Map.of("status", "OTP_SENT", "message", "Nouvel OTP envoyé"));
    }

    // ============================================================
    // KYC
    // ============================================================

    @PostMapping("/kyc/submit")
    @Operation(summary = "Soumettre le KYC (CIN)", description = "Citoyen soumet sa CIN pour validation agent")
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<CitizenProfileResponse> submitKyc(
            @RequestParam String citizenReference,
            @Valid @RequestBody KycSubmissionRequest request) {

        // Vérifier que le citoyen modifie son propre profil
        verifyOwnProfile(citizenReference);

        return ResponseEntity.ok(citizenService.submitKyc(citizenReference, request));
    }

    @PostMapping("/kyc/validate")
    @Operation(summary = "Valider/rejeter KYC (agent)", description = "Agent valide ou rejette le KYC soumis")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANK_AGENT')")
    public ResponseEntity<CitizenProfileResponse> validateKyc(@Valid @RequestBody KycDecisionRequest request) {

        String agentId = securityContextHelper.getCurrentUserId();
        return ResponseEntity.ok(citizenService.validateKyc(request, agentId));
    }

    // ============================================================
    // CONSULTATION
    // ============================================================

    @GetMapping("/profile/{citizenReference}")
    @Operation(summary = "Consulter un profil citoyen")
    @PreAuthorize("hasAnyRole('CITIZEN', 'ADMIN', 'BANK_AGENT', 'AUDITOR')")
    public ResponseEntity<CitizenProfileResponse> getProfile(
            @PathVariable String citizenReference) {

        return ResponseEntity.ok(citizenService.getProfile(citizenReference));
    }

    @GetMapping("/me")
    @Operation(summary = "Consulter mon propre profil (depuis JWT)")
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<CitizenProfileResponse> getMyProfile() {
        String userId = securityContextHelper.getCurrentUserId();
        Citizen citizen = citizenRepository.findByKeycloakUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException("PROFILE_NOT_FOUND",
                    "Profil non trouvé pour l'utilisateur courant"));
        return ResponseEntity.ok(citizenService.getProfile(citizen.getCitizenReference()));
    }

    @GetMapping("/search")
    @Operation(summary = "Rechercher des citoyens (admin/agent)")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANK_AGENT') or hasRole('AUDITOR')")
    public ResponseEntity<PageResponse<CitizenProfileResponse>> searchCitizens(
            @ParameterObject Pageable pageable,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) KycStatus kycStatus,
            @RequestParam(required = false) AccountStatus accountStatus) {

        var page = citizenRepository.searchCitizens(
                region,
                kycStatus != null ? kycStatus.name() : null,
                accountStatus != null ? accountStatus.name() : null,
                pageable
        ).map(citizen -> citizenService.getProfile(citizen.getCitizenReference()));

        return ResponseEntity.ok(PageResponse.from(page));
    }

    // ============================================================
    // ADMIN
    // ============================================================

    @PostMapping("/{citizenId}/suspend")
    @Operation(summary = "Suspendre un compte (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> suspendAccount(
            @PathVariable UUID citizenId,
            @RequestParam String reason) {
        citizenService.suspendAccount(citizenId, reason);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{citizenId}/reactivate")
    @Operation(summary = "Réactiver un compte (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> reactivateAccount(@PathVariable UUID citizenId) {
        citizenService.reactivateAccount(citizenId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{citizenId}/close")
    @Operation(summary = "Clôturer un compte (admin) — anonymise les données (droit à l'oubli)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> closeAccount(
            @PathVariable UUID citizenId,
            @RequestParam String reason) {
        citizenService.closeAccount(citizenId, reason);
        return ResponseEntity.noContent().build();
    }

    // ============================================================
    // HEALTH
    // ============================================================

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "service", "trust-id",
            "status", "UP",
            "version", "0.1.0"
        ));
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private void verifyOwnProfile(String citizenReference) {
        // Un citoyen ne peut modifier que son propre profil
        if (securityContextHelper.hasRole("CITIZEN") && !securityContextHelper.hasRole("ADMIN")) {
            Citizen citizen = citizenService.getCitizenByReference(citizenReference);
            String currentKeycloakId = securityContextHelper.getCurrentUserId();
            if (!currentKeycloakId.equals(citizen.getKeycloakUserId())) {
                throw new BusinessException("FORBIDDEN_OWN_PROFILE",
                    "Vous ne pouvez modifier que votre propre profil");
            }
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
