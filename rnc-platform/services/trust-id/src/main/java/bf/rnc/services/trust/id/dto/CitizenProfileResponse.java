package bf.rnc.services.trust.id.dto;

import bf.rnc.services.trust.id.entity.AccountStatus;
import bf.rnc.services.trust.id.entity.KycStatus;
import bf.rnc.services.trust.id.entity.PepStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Vue publique du profil citoyen (réponse API).
 * Ne contient aucune donnée chiffrée — seulement des données déchiffrées et masquées.
 */
@Getter
@Builder
public class CitizenProfileResponse {

    private UUID id;
    private String citizenReference;
    private String phoneNumberMasked;  // ex: +226 6 ** ** ** 12
    private String emailMasked;
    private String firstName;
    private String lastName;
    private String gender;
    private String region;
    private String province;
    private String commune;
    private String village;

    private KycStatus kycStatus;
    private Instant kycVerifiedAt;
    private Boolean sanctionsScreened;
    private Boolean sanctionsHit;
    private PepStatus pepStatus;

    private AccountStatus accountStatus;
    private Boolean phoneVerified;
    private Boolean emailVerified;
    private Boolean consentDataProcessing;
    private Boolean consentMarketing;
    private Boolean consentDataSharing;

    private Instant createdAt;
    private Instant updatedAt;

    /** Indique si le compte est pleinement actif (OTP vérifié + KYC validé) */
    public boolean isFullyActive() {
        return accountStatus == AccountStatus.ACTIVE && kycStatus == KycStatus.VERIFIED;
    }
}
