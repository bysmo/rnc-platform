package bf.rnc.services.trust.credit.repository;

import bf.rnc.services.trust.credit.entity.InstallmentStatus;
import bf.rnc.services.trust.credit.entity.RepaymentInstallment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RepaymentInstallmentRepository extends JpaRepository<RepaymentInstallment, UUID> {

    List<RepaymentInstallment> findByCreditIdAndDeletedFalseOrderByInstallmentNumberAsc(UUID creditId);

    @Query("SELECT i FROM RepaymentInstallment i WHERE i.deleted = false " +
           "AND i.status IN ('PENDING','PARTIALLY_PAID') " +
           "AND i.dueDate <= :date ORDER BY i.dueDate ASC")
    List<RepaymentInstallment> findDueInstallments(@Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(i.paidMinor), 0) FROM RepaymentInstallment i " +
           "WHERE i.deleted = false AND i.creditId = :creditId")
    long sumPaidForCredit(@Param("creditId") UUID creditId);

    @Query("SELECT COUNT(i) FROM RepaymentInstallment i WHERE i.deleted = false " +
           "AND i.creditId = :creditId AND i.status = 'PAID'")
    int countPaidInstallments(@Param("creditId") UUID creditId);

    @Query("SELECT COUNT(i) FROM RepaymentInstallment i WHERE i.deleted = false " +
           "AND i.creditId = :creditId")
    int countTotalInstallments(@Param("creditId") UUID creditId);
}
