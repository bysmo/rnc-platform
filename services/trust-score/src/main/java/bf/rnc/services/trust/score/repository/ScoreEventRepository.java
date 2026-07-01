package bf.rnc.services.trust.score.repository;

import bf.rnc.services.trust.score.entity.ScoreEvent;
import bf.rnc.services.trust.score.entity.ScoreEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ScoreEventRepository extends JpaRepository<ScoreEvent, UUID> {

    /**
     * Récupère tous les événements d'un citoyen, triés par date (plus récent d'abord).
     */
    Page<ScoreEvent> findByCitizenIdAndDeletedFalseOrderByEventTimestampDesc(
            String citizenId, Pageable pageable);

    /**
     * Compte les événements négatifs sur une période (indicateur de risque).
     */
    @Query("SELECT COUNT(e) FROM ScoreEvent e WHERE e.citizenId = :citizenId " +
           "AND e.impact < 0 AND e.eventTimestamp > :since")
    long countNegativeEventsSince(@Param("citizenId") String citizenId,
                                   @Param("since") Instant since);

    /**
     * Somme des impacts sur une période (pour vérifier la cohérence du score).
     */
    @Query("SELECT COALESCE(SUM(e.impact), 0) FROM ScoreEvent e WHERE e.citizenId = :citizenId " +
           "AND e.eventTimestamp > :since")
    int sumImpactSince(@Param("citizenId") String citizenId,
                       @Param("since") Instant since);

    /**
     * Récupère les événements par référence externe (ex: credit_id).
     */
    List<ScoreEvent> findByEventReferenceAndDeletedFalse(String eventReference);

    /**
     * Vérifie si un événement a déjà été traité (idempotence).
     */
    boolean existsByCitizenIdAndEventTypeAndEventReferenceAndDeletedFalse(
            String citizenId, ScoreEventType eventType, String eventReference);
}
