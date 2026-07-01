package bf.rnc.services.trust.credit.repository;

import bf.rnc.services.trust.credit.entity.CreditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CreditEventRepository extends JpaRepository<CreditEvent, UUID> {

    List<CreditEvent> findByCreditIdOrderByEventTimestampDesc(UUID creditId);
}
