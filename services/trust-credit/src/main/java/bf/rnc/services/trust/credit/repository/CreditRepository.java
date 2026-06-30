package bf.rnc.services.trust.credit.repository;

import bf.rnc.services.trust.credit.entity.Credit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CreditRepository extends JpaRepository<Credit, UUID> {

    Optional<Credit> findByCreditReference(String reference);

    Page<Credit> findByCitizenIdOrderByCreatedAtDesc(String citizenId, Pageable pageable);

    @Query("SELECT COUNT(c) FROM Credit c WHERE c.status = 'ACTIVE' AND c.citizenId = :citizenId")
    long countActiveCreditsForCitizen(@Param("citizenId") String citizenId);

    @Query("SELECT COALESCE(SUM(c.amountMinor), 0) FROM Credit c " +
           "WHERE c.status = 'ACTIVE' AND c.citizenId = :citizenId")
    long totalOutstandingForCitizen(@Param("citizenId") String citizenId);
}
