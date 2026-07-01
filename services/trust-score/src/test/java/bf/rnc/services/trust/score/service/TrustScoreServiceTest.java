package bf.rnc.services.trust.score.service;

import bf.rnc.common.lib.exception.BusinessException;
import bf.rnc.common.lib.exception.NotFoundException;
import bf.rnc.services.trust.score.dto.AppealDecisionRequest;
import bf.rnc.services.trust.score.dto.ScoreAppealRequest;
import bf.rnc.services.trust.score.dto.ScoreEventRequest;
import bf.rnc.services.trust.score.entity.*;
import bf.rnc.services.trust.score.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires du TrustScoreService — logique métier isolée (sans DB réelle).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TrustScoreService — Tests unitaires")
class TrustScoreServiceTest {

    @Mock private ScoreRepository scoreRepository;
    @Mock private ScoreEventRepository scoreEventRepository;
    @Mock private ScoreHistoryRepository scoreHistoryRepository;
    @Mock private ScoreAppealRepository scoreAppealRepository;
    @Mock private ScoreFactorConfigRepository factorConfigRepository;

    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TrustScoreService scoreService;

    @BeforeEach
    void setUp() {
        when(factorConfigRepository.findByEnabledTrue()).thenReturn(List.of());
    }

    // ============================================================
    // INITIALISATION
    // ============================================================

    @Nested
    @DisplayName("initializeScore()")
    class InitializeScore {

        @Test
        @DisplayName("✓ Initialise un score à 500 (FAIR) pour un nouveau citoyen")
        void shouldInitializeScoreForNewCitizen() {
            String citizenId = "citizen-001";
            when(scoreRepository.existsByCitizenIdAndDeletedFalse(citizenId)).thenReturn(false);
            when(scoreRepository.save(any(Score.class))).thenAnswer(inv -> {
                Score s = inv.getArgument(0);
                s.setId(UUID.randomUUID());
                return s;
            });

            Score score = scoreService.initializeScore(citizenId);

            assertThat(score.getScoreValue()).isEqualTo(500);
            assertThat(score.getScoreLevel()).isEqualTo(ScoreLevel.FAIR);
            assertThat(score.getScoreVersion()).isEqualTo(1);
            verify(scoreHistoryRepository).save(any(ScoreHistory.class));
        }

        @Test
        @DisplayName("✗ Refus si score déjà existant")
        void shouldRejectIfScoreExists() {
            String citizenId = "citizen-001";
            when(scoreRepository.existsByCitizenIdAndDeletedFalse(citizenId)).thenReturn(true);

            assertThatThrownBy(() -> scoreService.initializeScore(citizenId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("existe déjà");

            verify(scoreRepository, never()).save(any());
        }
    }

    // ============================================================
    // ÉVÉNEMENTS DE SCORE
    // ============================================================

    @Nested
    @DisplayName("recordEvent()")
    class RecordEvent {

        @Test
        @DisplayName("✓ Événement positif augmente le score")
        void shouldIncreaseScoreOnPositiveEvent() {
            String citizenId = "citizen-001";
            Score existingScore = buildScore(citizenId, 500, ScoreLevel.FAIR);

            when(scoreEventRepository.existsByCitizenIdAndEventTypeAndEventReferenceAndDeletedFalse(
                    any(), any(), any())).thenReturn(false);
            when(scoreRepository.findByCitizenIdAndDeletedFalse(citizenId))
                    .thenReturn(Optional.of(existingScore));
            when(scoreEventRepository.findByCitizenIdAndDeletedFalseOrderByEventTimestampDesc(
                    any(), any()))
                    .thenReturn(new PageImpl<>(List.of(
                            buildEvent(citizenId, ScoreEventType.CREDIT_REPAID_ON_TIME, 20)
                    )));
            when(scoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ScoreEventRequest request = ScoreEventRequest.builder()
                    .citizenId(citizenId)
                    .eventType(ScoreEventType.CREDIT_REPAID_ON_TIME)
                    .eventReference("CR-ABC123")
                    .eventTimestamp(Instant.now())
                    .build();

            Score result = scoreService.recordEvent(request);

            assertThat(result.getScoreValue()).isEqualTo(520); // 500 + 20
            verify(scoreEventRepository).save(any(ScoreEvent.class));
            verify(scoreHistoryRepository).save(any(ScoreHistory.class));
        }

        @Test
        @DisplayName("✓ Événement négatif diminue le score (jusqu'à 0 min)")
        void shouldDecreaseScoreOnNegativeEvent() {
            String citizenId = "citizen-001";
            Score existingScore = buildScore(citizenId, 50, ScoreLevel.CRITICAL);

            when(scoreEventRepository.existsByCitizenIdAndEventTypeAndEventReferenceAndDeletedFalse(
                    any(), any(), any())).thenReturn(false);
            when(scoreRepository.findByCitizenIdAndDeletedFalse(citizenId))
                    .thenReturn(Optional.of(existingScore));
            when(scoreEventRepository.findByCitizenIdAndDeletedFalseOrderByEventTimestampDesc(
                    any(), any()))
                    .thenReturn(new PageImpl<>(List.of(
                            buildEvent(citizenId, ScoreEventType.CREDIT_DEFAULT, -100)
                    )));
            when(scoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ScoreEventRequest request = ScoreEventRequest.builder()
                    .citizenId(citizenId)
                    .eventType(ScoreEventType.CREDIT_DEFAULT)
                    .eventReference("CR-DEF456")
                    .build();

            Score result = scoreService.recordEvent(request);

            // 50 - 100 = -50, mais clampé à 0
            assertThat(result.getScoreValue()).isEqualTo(0);
            assertThat(result.getScoreLevel()).isEqualTo(ScoreLevel.CRITICAL);
        }

        @Test
        @DisplayName("✓ Idempotence : événement dupliqué ignoré")
        void shouldIgnoreDuplicateEvent() {
            String citizenId = "citizen-001";
            Score existingScore = buildScore(citizenId, 500, ScoreLevel.FAIR);

            when(scoreEventRepository.existsByCitizenIdAndEventTypeAndEventReferenceAndDeletedFalse(
                    eq(citizenId), eq(ScoreEventType.CREDIT_REPAID_ON_TIME), eq("CR-DUP")))
                    .thenReturn(true);
            when(scoreRepository.findByCitizenIdAndDeletedFalse(citizenId))
                    .thenReturn(Optional.of(existingScore));

            ScoreEventRequest request = ScoreEventRequest.builder()
                    .citizenId(citizenId)
                    .eventType(ScoreEventType.CREDIT_REPAID_ON_TIME)
                    .eventReference("CR-DUP")
                    .build();

            Score result = scoreService.recordEvent(request);

            // Aucun événement sauvegardé
            verify(scoreEventRepository, never()).save(any());
            assertThat(result.getScoreValue()).isEqualTo(500); // inchangé
        }

        @Test
        @DisplayName("✓ Score plafonné à 1000")
        void shouldCapScoreAt1000() {
            String citizenId = "citizen-001";
            Score existingScore = buildScore(citizenId, 990, ScoreLevel.EXCELLENT);

            when(scoreEventRepository.existsByCitizenIdAndEventTypeAndEventReferenceAndDeletedFalse(
                    any(), any(), any())).thenReturn(false);
            when(scoreRepository.findByCitizenIdAndDeletedFalse(citizenId))
                    .thenReturn(Optional.of(existingScore));
            when(scoreEventRepository.findByCitizenIdAndDeletedFalseOrderByEventTimestampDesc(
                    any(), any()))
                    .thenReturn(new PageImpl<>(List.of(
                            buildEvent(citizenId, ScoreEventType.CREDIT_REPAID_EARLY, 25)
                    )));
            when(scoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ScoreEventRequest request = ScoreEventRequest.builder()
                    .citizenId(citizenId)
                    .eventType(ScoreEventType.CREDIT_REPAID_EARLY)
                    .eventReference("CR-MAX")
                    .build();

            Score result = scoreService.recordEvent(request);

            // 990 + 25 = 1015, mais clampé à 1000
            assertThat(result.getScoreValue()).isEqualTo(1000);
            assertThat(result.getScoreLevel()).isEqualTo(ScoreLevel.EXCELLENT);
        }
    }

    // ============================================================
    // CONSULTATION
    // ============================================================

    @Nested
    @DisplayName("getScore()")
    class GetScore {

        @Test
        @DisplayName("✗NotFoundException si citoyen sans score")
        void shouldThrowIfNoScore() {
            when(scoreRepository.findByCitizenIdAndDeletedFalse("unknown"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> scoreService.getScore("unknown"))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("✓ isEligibleForAutomaticCredit true pour GOOD/EXCELLENT")
        void shouldBeEligibleForGoodScore() {
            Score goodScore = buildScore("citizen-001", 700, ScoreLevel.GOOD);
            when(scoreRepository.findByCitizenIdAndDeletedFalse("citizen-001"))
                    .thenReturn(Optional.of(goodScore));

            assertThat(scoreService.isEligibleForAutomaticCredit("citizen-001")).isTrue();
        }

        @Test
        @DisplayName("✗ isEligibleForAutomaticCredit false pour FAIR/LOW/CRITICAL")
        void shouldNotBeEligibleForFairScore() {
            Score fairScore = buildScore("citizen-001", 550, ScoreLevel.FAIR);
            when(scoreRepository.findByCitizenIdAndDeletedFalse("citizen-001"))
                    .thenReturn(Optional.of(fairScore));

            assertThat(scoreService.isEligibleForAutomaticCredit("citizen-001")).isFalse();
        }
    }

    // ============================================================
    // CONTESTATIONS
    // ============================================================

    @Nested
    @DisplayName("Appeals")
    class Appeals {

        @Test
        @DisplayName("✓ Soumission contestation")
        void shouldSubmitAppeal() {
            Score score = buildScore("citizen-001", 400, ScoreLevel.LOW);
            when(scoreRepository.findByCitizenIdAndDeletedFalse("citizen-001"))
                    .thenReturn(Optional.of(score));
            when(scoreAppealRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ScoreAppealRequest req = ScoreAppealRequest.builder()
                    .citizenId("citizen-001")
                    .reason("Je conteste ce score car j'ai remboursé un prêt familial")
                    .build();

            ScoreAppeal appeal = scoreService.submitAppeal(req);

            assertThat(appeal.getStatus()).isEqualTo(AppealStatus.PENDING);
            assertThat(appeal.getScoreAtAppeal()).isEqualTo(400);
        }

        @Test
        @DisplayName("✓ Approbation contestation avec nouveau score")
        void shouldApproveAppealWithNewScore() {
            ScoreAppeal appeal = buildAppeal("citizen-001", 400);
            Score score = buildScore("citizen-001", 400, ScoreLevel.LOW);

            when(scoreAppealRepository.findById(appeal.getId())).thenReturn(Optional.of(appeal));
            when(scoreRepository.findByCitizenIdAndDeletedFalse("citizen-001"))
                    .thenReturn(Optional.of(score));
            when(scoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AppealDecisionRequest req = AppealDecisionRequest.builder()
                    .appealId(appeal.getId())
                    .approved(true)
                    .newScore(550)
                    .reviewNotes("Vérifié — prêt familial remboursé")
                    .build();

            Score result = scoreService.reviewAppeal(req, "admin-001");

            assertThat(result.getScoreValue()).isEqualTo(550);
            assertThat(appeal.getStatus()).isEqualTo(AppealStatus.APPROVED);
            assertThat(appeal.getReviewedBy()).isEqualTo("admin-001");
        }

        @Test
        @DisplayName("✗ Refus de traiter une contestation déjà traitée")
        void shouldRejectAlreadyProcessedAppeal() {
            ScoreAppeal appeal = buildAppeal("citizen-001", 400);
            appeal.setStatus(AppealStatus.APPROVED);

            when(scoreAppealRepository.findById(appeal.getId())).thenReturn(Optional.of(appeal));

            AppealDecisionRequest req = AppealDecisionRequest.builder()
                    .appealId(appeal.getId())
                    .approved(false)
                    .build();

            assertThatThrownBy(() -> scoreService.reviewAppeal(req, "admin-001"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("déjà");
        }
    }

    // ============================================================
    // ADMIN
    // ============================================================

    @Nested
    @DisplayName("Manual adjustment")
    class ManualAdjustment {

        @Test
        @DisplayName("✓ Ajustement manuel valide")
        void shouldAdjustScoreManually() {
            Score score = buildScore("citizen-001", 500, ScoreLevel.FAIR);
            when(scoreRepository.findByCitizenIdAndDeletedFalse("citizen-001"))
                    .thenReturn(Optional.of(score));
            when(scoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Score result = scoreService.manualAdjustment("citizen-001", 750, "Correction erreur", "admin-001");

            assertThat(result.getScoreValue()).isEqualTo(750);
            assertThat(result.getScoreLevel()).isEqualTo(ScoreLevel.GOOD);
        }

        @Test
        @DisplayName("✗ Ajustement refusé si score hors bornes")
        void shouldRejectInvalidScore() {
            assertThatThrownBy(() -> scoreService.manualAdjustment("citizen-001", 1500, "Test", "admin-001"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("entre 0 et 1000");
        }
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private Score buildScore(String citizenId, int value, ScoreLevel level) {
        Score s = new Score();
        s.setId(UUID.randomUUID());
        s.setCitizenId(citizenId);
        s.setScoreValue(value);
        s.setScoreLevel(level);
        s.setComputedAt(Instant.now());
        s.setScoreVersion(1);
        s.setFactors("{}");
        return s;
    }

    private ScoreEvent buildEvent(String citizenId, ScoreEventType type, int impact) {
        ScoreEvent e = new ScoreEvent();
        e.setId(UUID.randomUUID());
        e.setCitizenId(citizenId);
        e.setEventType(type);
        e.setImpact(impact);
        e.setEventTimestamp(Instant.now());
        return e;
    }

    private ScoreAppeal buildAppeal(String citizenId, int scoreAtAppeal) {
        ScoreAppeal a = new ScoreAppeal();
        a.setId(UUID.randomUUID());
        a.setCitizenId(citizenId);
        a.setScoreAtAppeal(scoreAtAppeal);
        a.setReason("Contestation de test");
        a.setStatus(AppealStatus.PENDING);
        a.setSubmittedAt(Instant.now());
        return a;
    }
}
