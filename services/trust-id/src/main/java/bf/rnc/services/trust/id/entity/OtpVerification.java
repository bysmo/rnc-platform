package bf.rnc.services.trust.id.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Vérification OTP SMS pour inscription / changement téléphone / MFA.
 * Le code OTP n'est JAMAIS stocké en clair — uniquement son hash SHA-256.
 */
@Entity
@Table(name = "otp_verifications", schema = "trust_id")
@Getter
@Setter
public class OtpVerification {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "phone_number_hash", nullable = false, length = 128)
    private String phoneNumberHash;

    @Column(name = "otp_code_hash", nullable = false, length = 128)
    private String otpCodeHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private OtpPurpose purpose;

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 5;

    @Column(name = "verified", nullable = false)
    private Boolean verified = false;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * Vérifie si l'OTP a expiré.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Vérifie si l'OTP peut encore être tenté.
     */
    public boolean canRetry() {
        return !verified && !isExpired() && attempts < maxAttempts;
    }
}
