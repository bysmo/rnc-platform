package bf.rnc.services.trust.score.service;

import bf.rnc.common.lib.exception.BusinessException;
import bf.rnc.common.lib.exception.NotFoundException;
import bf.rnc.common.security.audit.Auditable;
import bf.rnc.services.trust.score.dto.*;
import bf.rnc.services.trust.score.entity.*;
import bf.rnc.services.trust.score.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service métier central de Trust Score.
 *
 * <p>Responsabilités :</p>
 * <ul>
 *   <li>Initialiser le score d'un nouveau citoyen (score initial = 500, niveau FAIR)</li>
 *   <li>Appliquer des événements de score (remboursement, retard, dette, etc.)</li>
 *   <li>Recalculer le score global après chaque événement</li>
 *   <li>Construire le détail des facteurs pour l'explicabilité</li>
 *   <li>Gérer les contestations citoyen (appeals)</li>
 *   <li>Fournir des analytics agrégés (admin/auditeur)</li>
 * </ul>
 *
 * <p>L'algorithme de scoring est entièrement explicable : chaque variation est tracée
 * dans {@link ScoreEvent}, et le détail des facteurs est calculé à chaque mise à jour.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrustScoreService {

    private static final int INITIAL_SCORE = 500;
    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 1000;

    private final ScoreRepository scoreRepository;
    private final ScoreEventRepository scoreEventRepository;
    private final ScoreHistoryRepository scoreHistoryRepository;
    private final ScoreAppealRepository scoreAppealRepository;
    private final ScoreFactorConfigRepository factorConfigRepository;
    private final ObjectMapper objectMapper;

    // ============================================================
    // INITIALISATION
    // ============================================================

    /**
     * Initialise le score d'un nouveau citoyen (appelé par Trust ID après inscription).
     */
    @Auditable(action = "INIT_SCORE", resourceType = "Score", actorType = "SYSTEM", extractResourceId = true)
    @Transactional
    public Score initializeScore(String citizenId) {
        if (scoreRepository.existsByCitizenIdAndDeletedFalse(citizenId)) {
            throw new BusinessException("SCORE_ALREADY_EXISTS",
                "Un score existe déjà pour ce citoyen");
        }

        Score score = new Score();
        score.setCitizenId(citizenId);
        score.setScoreValue(INITIAL_SCORE);
        score.setScoreLevel(ScoreLevel.fromScore(INITIAL_SCORE));
        score.setComputedAt(Instant.now());
        score.setScoreVersion(1);
        score.setFactors(buildInitialFactors());

        score = scoreRepository.save(score);

        // Historique immuable
        saveHistory(citizenId, INITIAL_SCORE, ScoreLevel.FAIR, ScoreHistorySource.INITIAL,
                "Score initial à l'inscription");

        log.info("Score initialisé pour citoyen {} = {} ({})",
                citizenId, INITIAL_SCORE, score.getScoreLevel());

        return score;
    }

    // ============================================================
    // ÉVÉNEMENTS DE SCORE
    // ============================================================

    /**
     * Enregistre un événement de score et recalcule le score du citoyen.
     *
     * <p>Idempotent : si l'événement a déjà été traité (même citizenId + type + reference),
     * il est ignoré silencieusement.</p>
     */
    @Auditable(action = "RECORD_SCORE_EVENT", resourceType = "Score", actorType = "SYSTEM")
    @Transactional
    public Score recordEvent(ScoreEventRequest request) {
        // Idempotence : vérifier si l'événement existe déjà
        if (request.getEventReference() != null &&
                scoreEventRepository.existsByCitizenIdAndEventTypeAndEventReferenceAndDeletedFalse(
                        request.getCitizenId(), request.getEventType(), request.getEventReference())) {
            log.info("Événement déjà traité — ignoré: citizen={}, type={}, ref={}",
                    request.getCitizenId(), request.getEventType(), request.getEventReference());
            return getOrCreateScore(request.getCitizenId());
        }

        // Créer l'événement
        ScoreEvent event = new ScoreEvent();
        event.setCitizenId(request.getCitizenId());
        event.setEventType(request.getEventType());
        event.setEventReference(request.getEventReference());
        event.setImpact(request.getEventType().getDefaultImpact());
        event.setDescription(request.getDescription() != null
                ? request.getDescription()
                : request.getEventType().getLabel());
        event.setEventTimestamp(request.getEventTimestamp() != null
                ? request.getEventTimestamp()
                : Instant.now());

        if (request.getMetadata() != null) {
            try {
                event.setMetadata(objectMapper.writeValueAsString(request.getMetadata()));
            } catch (JsonProcessingException e) {
                log.warn("Erreur sérialisation metadata", e);
            }
        }

        scoreEventRepository.save(event);

        // Recalculer le score
        return recalculateScore(request.getCitizenId(),
                "Événement: " + request.getEventType().getLabel());
    }

    // ============================================================
    // CONSULTATION
    // ============================================================

    @Transactional(readOnly = true)
    public ScoreResponse getScore(String citizenId) {
        Score score = scoreRepository.findByCitizenIdAndDeletedFalse(citizenId)
                .orElseThrow(() -> new NotFoundException("Score", citizenId));
        return toResponse(score);
    }

    @Transactional(readOnly = true)
    public Score getScoreEntity(String citizenId) {
        return scoreRepository.findByCitizenIdAndDeletedFalse(citizenId)
                .orElseThrow(() -> new NotFoundException("Score", citizenId));
    }

    /**
     * Récupère l'historique des événements de score d'un citoyen.
     */
    @Transactional(readOnly = true)
    public List<ScoreEventResponse> getEventHistory(String citizenId, int limit) {
        return scoreEventRepository
                .findByCitizenIdAndDeletedFalseOrderByEventTimestampDesc(
                        citizenId,
                        org.springframework.data.domain.PageRequest.of(0, limit))
                .stream()
                .map(ScoreEventResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Vérifie si un citoyen est éligible à un crédit automatique.
     */
    @Transactional(readOnly = true)
    public boolean isEligibleForAutomaticCredit(String citizenId) {
        return scoreRepository.findByCitizenIdAndDeletedFalse(citizenId)
                .map(s -> s.getScoreLevel().allowsAutomaticCredit())
                .orElse(false);
    }

    /**
     * Récupère les citoyens ayant un score critique (pour dispositif de redressement).
     */
    @Transactional(readOnly = true)
    public List<Score> getCriticalScores() {
        return scoreRepository.findCriticalScores(ScoreLevel.LOW.getMin());
    }

    // ============================================================
    // CONTESTATIONS (APPEALS)
    // ============================================================

    @Auditable(action = "SUBMIT_APPEAL", resourceType = "ScoreAppeal", actorType = "CITIZEN", extractResourceId = true)
    @Transactional
    public ScoreAppeal submitAppeal(ScoreAppealRequest request) {
        Score score = scoreRepository.findByCitizenIdAndDeletedFalse(request.getCitizenId())
                .orElseThrow(() -> new NotFoundException("Score", request.getCitizenId()));

        ScoreAppeal appeal = new ScoreAppeal();
        appeal.setCitizenId(request.getCitizenId());
        appeal.setScoreAtAppeal(score.getScoreValue());
        appeal.setReason(request.getReason());
        appeal.setStatus(AppealStatus.PENDING);

        return scoreAppealRepository.save(appeal);
    }

    @Auditable(action = "REVIEW_APPEAL", resourceType = "ScoreAppeal", actorType = "ADMIN")
    @Transactional
    public Score reviewAppeal(AppealDecisionRequest request, String reviewerId) {
        ScoreAppeal appeal = scoreAppealRepository.findById(request.getAppealId())
                .orElseThrow(() -> new NotFoundException("ScoreAppeal", request.getAppealId().toString()));

        if (appeal.getStatus() != AppealStatus.PENDING) {
            throw new BusinessException("APPEAL_ALREADY_PROCESSED",
                "Cette contestation a déjà été traitée");
        }

        appeal.setStatus(request.getApproved() ? AppealStatus.APPROVED : AppealStatus.REJECTED);
        appeal.setReviewedAt(Instant.now());
        appeal.setReviewedBy(reviewerId);
        appeal.setReviewNotes(request.getReviewNotes());
        scoreAppealRepository.save(appeal);

        if (request.getApproved()) {
            // Ajustement manuel du score
            Integer newScore = request.getNewScore() != null
                    ? request.getNewScore()
                    : appeal.getScoreAtAppeal() + 50; // bonus par défaut
            return adjustScore(appeal.getCitizenId(), newScore,
                    "Contestation acceptée par " + reviewerId);
        }

        return getScoreEntity(appeal.getCitizenId());
    }

    @Transactional(readOnly = true)
    public List<ScoreAppeal> getPendingAppeals() {
        return scoreAppealRepository.findByStatusAndDeletedFalseOrderBySubmittedAtAsc(AppealStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<ScoreAppeal> getCitizenAppeals(String citizenId) {
        return scoreAppealRepository.findByCitizenIdAndDeletedFalseOrderBySubmittedAtDesc(citizenId);
    }

    // ============================================================
    // ADMIN
    // ============================================================

    @Auditable(action = "MANUAL_ADJUST_SCORE", resourceType = "Score", actorType = "ADMIN")
    @Transactional
    public Score manualAdjustment(String citizenId, int newScore, String reason, String adminId) {
        if (newScore < MIN_SCORE || newScore > MAX_SCORE) {
            throw new BusinessException("INVALID_SCORE_VALUE",
                "Le score doit être entre " + MIN_SCORE + " et " + MAX_SCORE);
        }
        return adjustScore(citizenId, newScore, "Ajustement manuel par " + adminId + ": " + reason);
    }

    /**
     * Analytics agrégés — pour dashboard admin.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAnalytics() {
        Map<String, Object> analytics = new HashMap<>();

        // Distribution par niveau
        Map<ScoreLevel, Long> distribution = scoreRepository.countByLevel().stream()
                .collect(Collectors.toMap(
                        arr -> (ScoreLevel) arr[0],
                        arr -> (Long) arr[1]
                ));
        analytics.put("distributionByLevel", distribution);

        // Score moyen
        Double avg = scoreRepository.averageScore();
        analytics.put("averageScore", avg != null ? Math.round(avg) : null);

        // Nombre total de citoyens scorés
        analytics.put("totalScoredCitizens", scoreRepository.count());

        // Citoyens en score critique
        analytics.put("criticalCount", scoreRepository.findCriticalScores(ScoreLevel.LOW.getMin()).size());

        return analytics;
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private Score getOrCreateScore(String citizenId) {
        return scoreRepository.findByCitizenIdAndDeletedFalse(citizenId)
                .orElseGet(() -> initializeScore(citizenId));
    }

    private Score recalculateScore(String citizenId, String reason) {
        Score score = getOrCreateScore(citizenId);

        // Somme des impacts depuis la dernière mise à jour
        int newScoreValue = score.getScoreValue();
        // Note: pour un recalcul complet, on pourrait sommer depuis le début
        // mais pour la performance, on applique juste le dernier impact
        // (l'idempotence garantit qu'on ne double-compte pas)

        // Récupérer le dernier événement (le plus récent)
        List<ScoreEvent> recentEvents = scoreEventRepository
                .findByCitizenIdAndDeletedFalseOrderByEventTimestampDesc(
                        citizenId,
                        org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent();

        if (!recentEvents.isEmpty()) {
            ScoreEvent latest = recentEvents.get(0);
            newScoreValue = clamp(score.getScoreValue() + latest.getImpact());
        }

        score.setScoreValue(newScoreValue);
        score.recalculateLevel();
        score.setComputedAt(Instant.now());
        score.setScoreVersion(score.getScoreVersion() + 1);
        score.setFactors(buildFactorsWithEvent(citizenId));

        score = scoreRepository.save(score);

        // Historique immuable
        saveHistory(citizenId, newScoreValue, score.getScoreLevel(),
                ScoreHistorySource.COMPUTED, reason);

        log.info("Score recalculé pour {} = {} ({}) — raison: {}",
                citizenId, newScoreValue, score.getScoreLevel(), reason);

        return score;
    }

    private Score adjustScore(String citizenId, int newScoreValue, String reason) {
        Score score = getOrCreateScore(citizenId);
        int oldScore = score.getScoreValue();

        score.setScoreValue(clamp(newScoreValue));
        score.recalculateLevel();
        score.setComputedAt(Instant.now());
        score.setScoreVersion(score.getScoreVersion() + 1);

        score = scoreRepository.save(score);

        saveHistory(citizenId, score.getScoreValue(), score.getScoreLevel(),
                ScoreHistorySource.MANUAL_ADJUSTMENT,
                reason + " (de " + oldScore + " à " + score.getScoreValue() + ")");

        log.info("Score ajusté pour {} : {} → {} — raison: {}",
                citizenId, oldScore, score.getScoreValue(), reason);

        return score;
    }

    private void saveHistory(String citizenId, int value, ScoreLevel level,
                              ScoreHistorySource source, String notes) {
        ScoreHistory history = new ScoreHistory();
        history.setCitizenId(citizenId);
        history.setScoreValue(value);
        history.setScoreLevel(level);
        history.setSource(source);
        history.setNotes(notes);
        scoreHistoryRepository.save(history);
    }

    private int clamp(int value) {
        return Math.max(MIN_SCORE, Math.min(MAX_SCORE, value));
    }

    private String buildInitialFactors() {
        try {
            Map<String, Object> factors = new HashMap<>();
            factors.put("initial", true);
            factors.put("note", "Score initial — pas encore d'événements");
            return objectMapper.writeValueAsString(factors);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String buildFactorsWithEvent(String citizenId) {
        try {
            Map<String, Object> factors = new HashMap<>();

            // Compter les événements positifs et négatifs sur 12 mois
            Instant twelveMonthsAgo = Instant.now().minusSeconds(365L * 24 * 3600);
            long negativeCount = scoreEventRepository.countNegativeEventsSince(citizenId, twelveMonthsAgo);
            int recentImpact = scoreEventRepository.sumImpactSince(citizenId, twelveMonthsAgo);

            factors.put("negativeEventsLast12Months", negativeCount);
            factors.put("totalImpactLast12Months", recentImpact);
            factors.put("lastUpdate", Instant.now().toString());

            // Configuration des facteurs
            List<ScoreFactorConfig> configs = factorConfigRepository.findByEnabledTrue();
            Map<String, Object> factorWeights = configs.stream()
                    .collect(Collectors.toMap(
                            ScoreFactorConfig::getFactorKey,
                            c -> Map.of("weight", c.getWeight(), "label", c.getFactorLabel())
                    ));
            factors.put("configuredFactors", factorWeights);

            return objectMapper.writeValueAsString(factors);
        } catch (JsonProcessingException e) {
            log.warn("Erreur sérialisation factors", e);
            return "{}";
        }
    }

    private ScoreResponse toResponse(Score score) {
        // Parser le JSON des facteurs
        List<ScoreResponse.FactorDetail> factorDetails = List.of();
        try {
            Map<String, Object> factorsMap = objectMapper.readValue(
                    score.getFactors(), Map.class);

            // Si on a configuredFactors, on l'utilise
            Object configured = factorsMap.get("configuredFactors");
            if (configured instanceof Map) {
                Map<String, Object> configMap = (Map<String, Object>) configured;
                factorDetails = configMap.entrySet().stream()
                        .map(e -> {
                            Map<String, Object> val = (Map<String, Object>) e.getValue();
                            return ScoreResponse.FactorDetail.builder()
                                    .key(e.getKey())
                                    .label((String) val.get("label"))
                                    .weight((Integer) val.get("weight"))
                                    .build();
                        })
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Erreur parsing factors", e);
        }

        return ScoreResponse.builder()
                .id(score.getId())
                .citizenId(score.getCitizenId())
                .scoreValue(score.getScoreValue())
                .scoreLevel(score.getScoreLevel())
                .computedAt(score.getComputedAt())
                .version(score.getScoreVersion())
                .factors(factorDetails)
                .automaticCreditAllowed(score.getScoreLevel().allowsAutomaticCredit())
                .recommendedMaxAmount(getRecommendedMaxAmount(score.getScoreLevel()))
                .build();
    }

    /**
     * Montant maximum recommandé selon le niveau de score.
     */
    private Long getRecommendedMaxAmount(ScoreLevel level) {
        return switch (level) {
            case EXCELLENT -> 500_000L;
            case GOOD -> 200_000L;
            case FAIR -> 50_000L;
            case LOW -> 10_000L;
            case CRITICAL -> 0L;
        };
    }
}
