package bf.rnc.services.trust.id.repository;

import bf.rnc.services.trust.id.entity.OtpVerification;
import bf.rnc.services.trust.id.entity.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {

    /**
     * Récupère le dernier OTP valide (non consommé, non expiré) pour un téléphone + purpose.
     */
    @Query("SELECT o FROM OtpVerification o " +
           "WHERE o.phoneNumberHash = :phoneHash " +
           "AND o.purpose = :purpose " +
           "AND o.verified = false " +
           "AND o.consumedAt IS NULL " +
           "AND o.expiresAt > :now " +
           "ORDER BY o.createdAt DESC")
    Optional<OtpVerification> findLatestActiveOtp(
            @Param("phoneHash") String phoneHash,
            @Param("purpose") OtpPurpose purpose,
            @Param("now") Instant now);

    /**
     * Compte les OTP envoyés récemment pour éviter le spam (rate limiting).
     */
    @Query("SELECT COUNT(o) FROM OtpVerification o " +
           "WHERE o.phoneNumberHash = :phoneHash " +
           "AND o.purpose = :purpose " +
           "AND o.createdAt > :since")
    long countRecentOtps(
            @Param("phoneHash") String phoneHash,
            @Param("purpose") OtpPurpose purpose,
            @Param("since") Instant since);

    /**
     * Récupère les OTP expirés non consommés pour nettoyage.
     */
    @Query("SELECT o FROM OtpVerification o " +
           "WHERE o.verified = false " +
           "AND o.consumedAt IS NULL " +
           "AND o.expiresAt < :before")
    List<OtpVerification> findExpiredUnconsumed(@Param("before") Instant before);
}
