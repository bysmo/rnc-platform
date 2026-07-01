package bf.rnc.services.trust.score.repository;

import bf.rnc.services.trust.score.entity.ScoreHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ScoreHistoryRepository extends JpaRepository<ScoreHistory, UUID> {

    Page<ScoreHistory> findByCitizenIdOrderByRecordedAtDesc(String citizenId, Pageable pageable);
}
