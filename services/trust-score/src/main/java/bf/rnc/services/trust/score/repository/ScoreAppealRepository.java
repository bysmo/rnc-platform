package bf.rnc.services.trust.score.repository;

import bf.rnc.services.trust.score.entity.ScoreAppeal;
import bf.rnc.services.trust.score.entity.AppealStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScoreAppealRepository extends JpaRepository<ScoreAppeal, UUID> {

    List<ScoreAppeal> findByCitizenIdAndDeletedFalseOrderBySubmittedAtDesc(String citizenId);

    List<ScoreAppeal> findByStatusAndDeletedFalseOrderBySubmittedAtAsc(AppealStatus status);
}
