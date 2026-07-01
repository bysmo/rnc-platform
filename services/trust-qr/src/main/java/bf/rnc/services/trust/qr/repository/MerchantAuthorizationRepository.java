package bf.rnc.services.trust.qr.repository;

import bf.rnc.services.trust.qr.entity.MerchantAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantAuthorizationRepository extends JpaRepository<MerchantAuthorization, UUID> {

    @Query("SELECT a FROM MerchantAuthorization a WHERE a.deleted = false " +
           "AND a.merchantId = :merchantId AND a.citizenId = :citizenId " +
           "AND a.status = 'ACTIVE' AND (a.expiresAt IS NULL OR a.expiresAt > CURRENT_TIMESTAMP) " +
           "ORDER BY a.grantedAt DESC")
    Optional<MerchantAuthorization> findActiveAuthorization(
            @Param("merchantId") String merchantId,
            @Param("citizenId") String citizenId);

    @Query("SELECT a FROM MerchantAuthorization a WHERE a.deleted = false " +
           "AND a.status = 'ACTIVE' AND a.expiresAt IS NOT NULL " +
           "AND a.expiresAt < CURRENT_TIMESTAMP")
    List<MerchantAuthorization> findExpiredAuthorizations();
}
