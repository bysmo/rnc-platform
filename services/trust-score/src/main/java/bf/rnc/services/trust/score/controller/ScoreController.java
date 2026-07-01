package bf.rnc.services.trust.score.controller;

import bf.rnc.common.lib.dto.PageResponse;
import bf.rnc.common.lib.exception.BusinessException;
import bf.rnc.common.security.SecurityContextHelper;
import bf.rnc.services.trust.score.dto.*;
import bf.rnc.services.trust.score.entity.Score;
import bf.rnc.services.trust.score.entity.ScoreAppeal;
import bf.rnc.services.trust.score.service.TrustScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API REST — Trust Score : réputation financière nationale.
 *
 * <p>Tous les endpoints sont préfixés par <code>/api/v1/score</code>.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/score")
@RequiredArgsConstructor
@Tag(name = "Trust Score", description = "Réputation financière des citoyens RNC")
public class ScoreController {

    private final TrustScoreService scoreService;
    private final SecurityContextHelper securityContextHelper;

    // ============================================================
    // CONSULTATION
    // ============================================================

    @GetMapping("/{citizenId}")
    @Operation(summary = "Consulter le score d'un citoyen")
    @PreAuthorize("hasAnyRole('CITIZEN', 'ADMIN', 'BANK_AGENT', 'AUDITOR')")
    public ResponseEntity<ScoreResponse> getScore(@PathVariable String citizenId) {
        return ResponseEntity.ok(scoreService.getScore(citizenId));
    }

    @GetMapping("/me")
    @Operation(summary = "Consulter mon propre score (depuis JWT)")
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<ScoreResponse> getMyScore() {
        // Le citizenId doit être récupéré du JWT ou du service Trust ID
        // Pour le MVP, on l'extrait du JWT claim "sub"
        String citizenId = securityContextHelper.getCurrentUserId();
        return ResponseEntity.ok(scoreService.getScore(citizenId));
    }

    @GetMapping("/{citizenId}/events")
    @Operation(summary = "Historique des événements de score (explicabilité)")
    @PreAuthorize("hasAnyRole('CITIZEN', 'ADMIN', 'BANK_AGENT', 'AUDITOR')")
    public ResponseEntity<List<ScoreEventResponse>> getEventHistory(
            @PathVariable String citizenId,
            @RequestParam(defaultValue = "50") int limit) {
        if (limit < 1 || limit > 500) {
            throw new BusinessException("INVALID_LIMIT", "limit doit être entre 1 et 500");
        }
        return ResponseEntity.ok(scoreService.getEventHistory(citizenId, limit));
    }

    @GetMapping("/{citizenId}/eligibility")
    @Operation(summary = "Vérifier l'éligibilité au crédit automatique")
    @PreAuthorize("hasAnyRole('CITIZEN', 'ADMIN', 'BANK_AGENT')")
    public ResponseEntity<Map<String, Object>> checkEligibility(@PathVariable String citizenId) {
        boolean eligible = scoreService.isEligibleForAutomaticCredit(citizenId);
        return ResponseEntity.ok(Map.of(
                "citizenId", citizenId,
                "eligibleForAutomaticCredit", eligible
        ));
    }

    // ============================================================
    // ÉVÉNEMENTS (interne — appelé par Trust Credit, Trust Debt, etc.)
    // ============================================================

    @PostMapping("/events")
    @Operation(summary = "Enregistrer un événement de score (interne)")
    @PreAuthorize("hasAnyRole('SYSTEM', 'ADMIN')")
    public ResponseEntity<ScoreResponse> recordEvent(@Valid @RequestBody ScoreEventRequest request) {
        Score score = scoreService.recordEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(score));
    }

    @PostMapping("/initialize")
    @Operation(summary = "Initialiser le score d'un nouveau citoyen (interne)")
    @PreAuthorize("hasAnyRole('SYSTEM', 'ADMIN')")
    public ResponseEntity<ScoreResponse> initializeScore(@RequestParam String citizenId) {
        Score score = scoreService.initializeScore(citizenId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(score));
    }

    // ============================================================
    // CONTESTATIONS
    // ============================================================

    @PostMapping("/appeals")
    @Operation(summary = "Déposer une contestation de score")
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<ScoreAppeal> submitAppeal(@Valid @RequestBody ScoreAppealRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scoreService.submitAppeal(request));
    }

    @GetMapping("/appeals/pending")
    @Operation(summary = "Lister les contestations en attente (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ScoreAppeal>> getPendingAppeals() {
        return ResponseEntity.ok(scoreService.getPendingAppeals());
    }

    @GetMapping("/appeals/{citizenId}")
    @Operation(summary = "Lister les contestations d'un citoyen")
    @PreAuthorize("hasAnyRole('CITIZEN', 'ADMIN', 'AUDITOR')")
    public ResponseEntity<List<ScoreAppeal>> getCitizenAppeals(@PathVariable String citizenId) {
        return ResponseEntity.ok(scoreService.getCitizenAppeals(citizenId));
    }

    @PostMapping("/appeals/decision")
    @Operation(summary = "Décider d'une contestation (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ScoreResponse> reviewAppeal(@Valid @RequestBody AppealDecisionRequest request) {
        String reviewerId = securityContextHelper.getCurrentUserId();
        Score score = scoreService.reviewAppeal(request, reviewerId);
        return ResponseEntity.ok(toResponse(score));
    }

    // ============================================================
    // ADMIN
    // ============================================================

    @PostMapping("/adjust")
    @Operation(summary = "Ajustement manuel du score (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ScoreResponse> manualAdjustment(
            @RequestParam String citizenId,
            @RequestParam int newScore,
            @RequestParam String reason) {
        String adminId = securityContextHelper.getCurrentUserId();
        Score score = scoreService.manualAdjustment(citizenId, newScore, reason, adminId);
        return ResponseEntity.ok(toResponse(score));
    }

    @GetMapping("/analytics")
    @Operation(summary = "Analytics agrégés (admin/auditeur)")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<Map<String, Object>> getAnalytics() {
        return ResponseEntity.ok(scoreService.getAnalytics());
    }

    @GetMapping("/critical")
    @Operation(summary = "Lister les scores critiques (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Score>> getCriticalScores() {
        return ResponseEntity.ok(scoreService.getCriticalScores());
    }

    // ============================================================
    // HEALTH
    // ============================================================

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "service", "trust-score",
                "status", "UP",
                "version", "0.1.0"
        ));
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private ScoreResponse toResponse(Score score) {
        return ScoreResponse.builder()
                .id(score.getId())
                .citizenId(score.getCitizenId())
                .scoreValue(score.getScoreValue())
                .scoreLevel(score.getScoreLevel())
                .computedAt(score.getComputedAt())
                .version(score.getScoreVersion())
                .automaticCreditAllowed(score.getScoreLevel().allowsAutomaticCredit())
                .build();
    }
}
