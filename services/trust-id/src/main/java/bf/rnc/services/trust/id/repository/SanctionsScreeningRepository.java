package bf.rnc.services.trust.id.repository;

import bf.rnc.services.trust.id.entity.SanctionsScreening;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SanctionsScreeningRepository extends JpaRepository<SanctionsScreening, UUID> {

    List<SanctionsScreening> findByCitizenIdOrderByScreenDateDesc(UUID citizenId);
}
