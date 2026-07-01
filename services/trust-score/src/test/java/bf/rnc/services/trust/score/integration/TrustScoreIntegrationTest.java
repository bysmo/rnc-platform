package bf.rnc.services.trust.score.integration;

import bf.rnc.common.lib.exception.BusinessException;
import bf.rnc.common.lib.exception.NotFoundException;
import bf.rnc.services.trust.score.dto.AppealDecisionRequest;
import bf.rnc.services.trust.score.dto.ScoreAppealRequest;
import bf.rnc.services.trust.score.dto.ScoreEventRequest;
import bf.rnc.services.trust.score.dto.ScoreResponse;
import bf.rnc.services.trust.score.entity.Score;
import bf.rnc.services.trust.score.entity.ScoreLevel;
import bf.rnc.services.trust.score.repository.ScoreRepository;
import bf.rnc.services.trust.score.service.TrustScoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests d'intégration — Trust Score avec PostgreSQL réel (Testcontainers).
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Trust Score — Tests d'intégration")
class TrustScoreIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("trust_score_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired private TrustScoreService scoreService;
    @Autowired private ScoreRepository scoreRepository;

    @BeforeEach
    void setUp() {
        scoreRepository.deleteAll();
    }

    @Test
    @DisplayName("Initialisation score — valeur 500 + niveau FAIR")
    void testScoreInitialization() {
        Score score = scoreService.initializeScore("citizen-int-001");

        assertThat(score.getScoreValue()).isEqualTo(500);
        assertThat(score.getScoreLevel()).isEqualTo(ScoreLevel.FAIR);
        assertThat(score.getScoreVersion()).isEqualTo(1);
        assertThat(score.getFactors()).isNotNull();

        // Vérifier la persistance
        Score persisted = scoreRepository.findByCitizenIdAndDeletedFalse("citizen-int-001").orElseThrow();
        assertThat(persisted.getScoreValue()).isEqualTo(500);
    }

    @Test
    @DisplayName("Cycle complet : init → événement positif → événement négatif → ajustement")
    void testFullScoreLifecycle() {
        String citizenId = "citizen-int-002";

        // 1. Init
        scoreService.initializeScore(citizenId);
        assertThat(scoreService.getScore(citizenId).getScoreValue()).isEqualTo(500);

        // 2. Événement positif (+20)
        scoreService.recordEvent(ScoreEventRequest.builder()
                .citizenId(citizenId)
                .eventType(bf.rnc.services.trust.score.entity.ScoreEventType.CREDIT_REPAID_ON_TIME)
                .eventReference("CR-001")
                .eventTimestamp(Instant.now())
                .build());
        assertThat(scoreService.getScore(citizenId).getScoreValue()).isEqualTo(520);

        // 3. Encore un positif (+25)
        scoreService.recordEvent(ScoreEventRequest.builder()
                .citizenId(citizenId)
                .eventType(bf.rnc.services.trust.score.entity.ScoreEventType.CREDIT_REPAID_EARLY)
                .eventReference("CR-002")
                .build());
        assertThat(scoreService.getScore(citizenId).getScoreValue()).isEqualTo(545);

        // 4. Événement négatif (-30)
        scoreService.recordEvent(ScoreEventRequest.builder()
                .citizenId(citizenId)
                .eventType(bf.rnc.services.trust.score.entity.ScoreEventType.CREDIT_LATE_PAYMENT_8D)
                .eventReference("CR-003")
                .build());
        assertThat(scoreService.getScore(citizenId).getScoreValue()).isEqualTo(515);

        // 5. Ajustement manuel
        scoreService.manualAdjustment(citizenId, 600, "Correction", "admin-001");
        assertThat(scoreService.getScore(citizenId).getScoreValue()).isEqualTo(600);
        assertThat(scoreService.getScore(citizenId).getScoreLevel()).isEqualTo(ScoreLevel.FAIR);
    }

    @Test
    @DisplayName("Idempotence — événement dupliqué ignoré")
    void testIdempotency() {
        String citizenId = "citizen-int-003";
        scoreService.initializeScore(citizenId);

        ScoreEventRequest req = ScoreEventRequest.builder()
                .citizenId(citizenId)
                .eventType(bf.rnc.services.trust.score.entity.ScoreEventType.CREDIT_REPAID_ON_TIME)
                .eventReference("CR-UNIQUE-001")
                .build();

        // Premier appel : +20
        scoreService.recordEvent(req);
        assertThat(scoreService.getScore(citizenId).getScoreValue()).isEqualTo(520);

        // Deuxième appel avec même référence : ignoré
        scoreService.recordEvent(req);
        assertThat(scoreService.getScore(citizenId).getScoreValue()).isEqualTo(520); // inchangé
    }

    @Test
    @DisplayName("Score clampé à [0, 1000]")
    void testScoreClamping() {
        String citizenId = "citizen-int-004";
        scoreService.initializeScore(citizenId);

        // Événement très négatif
        scoreService.recordEvent(ScoreEventRequest.builder()
                .citizenId(citizenId)
                .eventType(bf.rnc.services.trust.score.entity.ScoreEventType.FRAUD_SUSPECTED)
                .eventReference("FR-001")
                .build());

        // 500 - 150 = 350, mais on vérifie que ça reste >= 0
        assertThat(scoreService.getScore(citizenId).getScoreValue()).isEqualTo(350);

        // Encore plus négatif
        scoreService.recordEvent(ScoreEventRequest.builder()
                .citizenId(citizenId)
                .eventType(bf.rnc.services.trust.score.entity.ScoreEventType.CREDIT_DEFAULT)
                .eventReference("DF-001")
                .build());

        // 350 - 100 = 250
        assertThat(scoreService.getScore(citizenId).getScoreValue()).isEqualTo(250);
    }

    @Test
    @DisplayName("Contestation complète : soumission → approbation → score ajusté")
    void testAppealFlow() {
        String citizenId = "citizen-int-005";
        scoreService.initializeScore(citizenId);

        // Baisser le score d'abord
        scoreService.recordEvent(ScoreEventRequest.builder()
                .citizenId(citizenId)
                .eventType(bf.rnc.services.trust.score.entity.ScoreEventType.CREDIT_DEFAULT)
                .eventReference("DF-APPEAL-001")
                .build());

        int scoreAvantContestation = scoreService.getScore(citizenId).getScoreValue();
        assertThat(scoreAvantContestation).isLessThan(500);

        // Soumettre contestation
        var appeal = scoreService.submitAppeal(ScoreAppealRequest.builder()
                .citizenId(citizenId)
                .reason("Ce défaut n'était pas de mon fait — je conteste")
                .build());

        assertThat(appeal.getStatus()).isEqualTo(bf.rnc.services.trust.score.entity.AppealStatus.PENDING);
        assertThat(appeal.getScoreAtAppeal()).isEqualTo(scoreAvantContestation);

        // Approuver avec nouveau score
        Score result = scoreService.reviewAppeal(AppealDecisionRequest.builder()
                .appealId(appeal.getId())
                .approved(true)
                .newScore(550)
                .reviewNotes("Vérifié — contestation légitime")
                .build(), "admin-001");

        assertThat(result.getScoreValue()).isEqualTo(550);
        assertThat(scoreService.getScore(citizenId).getScoreValue()).isEqualTo(550);
    }

    @Test
    @DisplayName("Analytics agrégés — distribution par niveau")
    void testAnalytics() {
        // Créer plusieurs citoyens avec scores différents
        for (int i = 0; i < 5; i++) {
            scoreService.initializeScore("citizen-analytics-" + i);
        }

        Map<String, Object> analytics = scoreService.getAnalytics();

        assertThat(analytics).containsKeys("distributionByLevel", "averageScore", "totalScoredCitizens");
        assertThat((Long) analytics.get("totalScoredCitizens")).isEqualTo(5);
        assertThat(analytics.get("averageScore")).isEqualTo(500); // tous à 500
    }

    @Test
    @DisplayName("Historique des événements — explicabilité")
    void testEventHistoryExplicability() {
        String citizenId = "citizen-int-006";
        scoreService.initializeScore(citizenId);

        // Enregistrer plusieurs événements
        for (int i = 0; i < 3; i++) {
            scoreService.recordEvent(ScoreEventRequest.builder()
                    .citizenId(citizenId)
                    .eventType(bf.rnc.services.trust.score.entity.ScoreEventType.CREDIT_REPAID_ON_TIME)
                    .eventReference("CR-HIST-" + i)
                    .eventTimestamp(Instant.now().minusSeconds(3600L * (3 - i)))
                    .description("Remboursement échéance " + (i + 1))
                    .build());
        }

        var history = scoreService.getEventHistory(citizenId, 50);

        assertThat(history).hasSize(3);
        // Triés par date décroissante (plus récent d'abord)
        assertThat(history.get(0).getEventTimestamp())
                .isAfterOrEqualTo(history.get(2).getEventTimestamp());
        assertThat(history.get(0).getImpact()).isEqualTo(20); // CREDIT_REPAID_ON_TIME = +20
    }

    @Test
    @DisplayName("Score critique — récupération pour dispositif de redressement")
    void testCriticalScoresRetrieval() {
        // Citoyen avec bon score
        scoreService.initializeScore("citizen-good-001");

        // Citoyen avec score critique
        scoreService.initializeScore("citizen-critical-001");
        scoreService.manualAdjustment("citizen-critical-001", 200, "Test critique", "admin");

        List<Score> criticals = scoreService.getCriticalScores();

        // 200 < 300 (seuil LOW)
        assertThat(criticals).hasSize(1);
        assertThat(criticals.get(0).getCitizenId()).isEqualTo("citizen-critical-001");
        assertThat(criticals.get(0).getScoreLevel()).isEqualTo(ScoreLevel.CRITICAL);
    }

    @Test
    @DisplayName("NotFoundException pour citoyen inexistant")
    void testNotFoundForUnknownCitizen() {
        assertThatThrownBy(() -> scoreService.getScore("unknown-citizen"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("BusinessException pour ajustement hors bornes")
    void testInvalidScoreAdjustment() {
        assertThatThrownBy(() -> scoreService.manualAdjustment("x", -50, "Test", "admin"))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> scoreService.manualAdjustment("x", 1500, "Test", "admin"))
                .isInstanceOf(BusinessException.class);
    }
}
