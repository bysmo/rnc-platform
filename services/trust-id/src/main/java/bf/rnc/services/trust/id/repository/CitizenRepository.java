package bf.rnc.services.trust.id.repository;

import bf.rnc.services.trust.id.entity.Citizen;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CitizenRepository extends JpaRepository<Citizen, UUID> {

    Optional<Citizen> findByCitizenReferenceAndDeletedFalse(String citizenReference);

    Optional<Citizen> findByPhoneNumberHashAndDeletedFalse(String phoneNumberHash);

    Optional<Citizen> findByCinNumberHashAndDeletedFalse(String cinNumberHash);

    Optional<Citizen> findByKeycloakUserIdAndDeletedFalse(String keycloakUserId);

    boolean existsByPhoneNumberHashAndDeletedFalse(String phoneNumberHash);

    boolean existsByCinNumberHashAndDeletedFalse(String cinNumberHash);

    @Query("SELECT c FROM Citizen c WHERE c.deleted = false " +
           "AND (:region IS NULL OR c.region = :region) " +
           "AND (:kycStatus IS NULL OR c.kycStatus = :kycStatus) " +
           "AND (:accountStatus IS NULL OR c.accountStatus = :accountStatus) " +
           "ORDER BY c.createdAt DESC")
    Page<Citizen> searchCitizens(
            @Param("region") String region,
            @Param("kycStatus") String kycStatus,
            @Param("accountStatus") String accountStatus,
            Pageable pageable);
}
