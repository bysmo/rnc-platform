package bf.rnc.services.trust.score.repository;

import bf.rnc.services.trust.score.entity.Score;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ScoreRepository extends JpaRepository<Score, UUID> {

    /**
     * Récupère le score actif d'un citoyen (une seule ligne grâce à l'index unique partiel).
     */
    Optional<Score> findByCitizenIdAndDeletedFalse(String citizenId);

    /**
     * Vérifie si un citoyen a déjà un score.
     */
    boolean existsByCitizenIdAndDeletedFalse(String citizenId);

    /**
     * Compte les citoyens par niveau de score (pour analytics).
     */
    @Query("SELECT s.scoreLevel, COUNT(s) FROM Score s WHERE s.deleted = false GROUP BY s.scoreLevel")
    java.util.List<Object[]> countByLevel();

    /**
     * Score moyen de tous les citoyens actifs.
     */
    @Query("SELECT AVG(s.scoreValue) FROM Score s WHERE s.deleted = false")
    Double averageScore();

    /**
     * Récupère les scores les plus bas (pour dispositif de redressement).
     */
    @Query("SELECT s FROM Score s WHERE s.deleted = false AND s.scoreValue < :threshold ORDER BY s.scoreValue ASC")
    java.util.List<Score> findCriticalScores(@Param("threshold") int threshold);
}
