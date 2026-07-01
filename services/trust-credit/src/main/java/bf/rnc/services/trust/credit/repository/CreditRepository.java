package bf.rnc.services.trust.credit.repository;

import bf.rnc.services.trust.credit.entity.Credit;
import bf.rnc.services.trust.credit.entity.CreditStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditRepository extends JpaRepository<Credit, UUID> {

    Optional<Credit> findByCreditReferenceAndDeletedFalse(String creditReference);

    Page<Credit> findByCitizenIdAndDeletedFalseOrderByCreatedAtDesc(String citizenId, Pageable pageable);

    Page<Credit> findByStatusAndDeletedFalseOrderByCreatedAtDesc(CreditStatus status, Pageable pageable);

    @Query("SELECT COUNT(c) FROM Credit c WHERE c.deleted = false " +
           "AND c.citizenId = :citizenId AND c.status IN :activeStatuses")
    long countActiveCreditsForCitizen(
            @Param("citizenId") String citizenId,
            @Param("activeStatuses") List<CreditStatus> activeStatuses);

    @Query("SELECT COALESCE(SUM(c.amountMinor), 0) FROM Credit c " +
           "WHERE c.deleted = false AND c.citizenId = :citizenId " +
           "AND c.status IN :activeStatuses")
    long totalOutstandingForCitizen(
            @Param("citizenId") String citizenId,
            @Param("activeStatuses") List<CreditStatus> activeStatuses);

    /**
     * Échéances échues aujourd'hui (pour rappels automatiques).
     */
    @Query("SELECT c FROM Credit c WHERE c.deleted = false AND c.status = 'ACTIVE' " +
           "AND c.maturityDate <= :date ORDER BY c.maturityDate ASC")
    List<Credit> findCreditsMaturedBefore(@Param("date") LocalDate date);

    /**
     * Crédits par marchand (pour dashboard marchand).
     */
    Page<Credit> findByMerchantIdAndDeletedFalseOrderByCreatedAtDesc(String merchantId, Pageable pageable);
}
