package bf.rnc.services.trust.id.repository;

import bf.rnc.services.trust.id.entity.ConsentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConsentHistoryRepository extends JpaRepository<ConsentHistory, UUID> {

    List<ConsentHistory> findByCitizenIdOrderByGrantedAtDesc(UUID citizenId);
}
