package bf.rnc.services.trust.id.entity;

import bf.rnc.common.lib.audit.BaseAuditFields;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Citoyen RNC — compte confiance.
 *
 * <p>Conformité Loi 010-2004/AN : les données personnelles sont chiffrées au repos
 * (champs *_encrypted) et un hash (champs *_hash) permet la recherche exacte.</p>
 */
@Entity
@Table(name = "citizens", schema = "trust_id")
@Getter
@Setter
public class Citizen extends BaseAuditFields {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Référence publique du citoyen (format CIT-XXXXXXXXXXXX) */
    @Column(name = "citizen_reference", nullable = false, unique = true, length = 50)
    private String citizenReference;

    /** ID utilisateur Keycloak (null jusqu'à activation du compte) */
    @Column(name = "keycloak_user_id", unique = true, length = 100)
    private String keycloakUserId;

    // ────────────────────────────────────────────────────────────
    // Données chiffrées (AES-256-GCM) + hash pour recherche
    // ────────────────────────────────────────────────────────────

    @Column(name = "phone_number_encrypted", nullable = false, length = 500)
    private String phoneNumberEncrypted;

    @Column(name = "phone_number_hash", nullable = false, length = 128)
    private String phoneNumberHash;

    @Column(name = "phone_number_verified", nullable = false)
    private Boolean phoneNumberVerified = false;

    @Column(name = "email_encrypted", length = 500)
    private String emailEncrypted;

    @Column(name = "email_hash", length = 128)
    private String emailHash;

    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Column(name = "first_name_encrypted", nullable = false, length = 500)
    private String firstNameEncrypted;

    @Column(name = "last_name_encrypted", nullable = false, length = 500)
    private String lastNameEncrypted;

    @Column(name = "date_of_birth_encrypted", length = 500)
    private String dateOfBirthEncrypted;

    @Column(name = "gender", length = 10)
    private String gender;

    // ────────────────────────────────────────────────────────────
    // Identité Burkina Faso
    // ────────────────────────────────────────────────────────────

    @Column(name = "cin_number_encrypted", length = 500)
    private String cinNumberEncrypted;

    @Column(name = "cin_number_hash", unique = true, length = 128)
    private String cinNumberHash;

    @Column(name = "cin_issue_date")
    private LocalDate cinIssueDate;

    @Column(name = "cin_issue_place", length = 100)
    private String cinIssuePlace;

    // ────────────────────────────────────────────────────────────
    // Localisation (en clair — non sensible)
    // ────────────────────────────────────────────────────────────

    @Column(name = "region", nullable = false, length = 50)
    private String region;

    @Column(name = "province", nullable = false, length = 50)
    private String province;

    @Column(name = "commune", nullable = false, length = 50)
    private String commune;

    @Column(name = "village", length = 100)
    private String village;

    @Column(name = "address_encrypted", length = 1000)
    private String addressEncrypted;

    // ────────────────────────────────────────────────────────────
    // KYC / LCB-FT
    // ────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 30)
    private KycStatus kycStatus = KycStatus.PENDING;

    @Column(name = "kyc_verified_at")
    private Instant kycVerifiedAt;

    @Column(name = "kyc_verified_by", length = 100)
    private String kycVerifiedBy;

    @Column(name = "kyc_rejection_reason", length = 500)
    private String kycRejectionReason;

    @Column(name = "sanctions_screened", nullable = false)
    private Boolean sanctionsScreened = false;

    @Column(name = "sanctions_screened_at")
    private Instant sanctionsScreenedAt;

    @Column(name = "sanctions_hit", nullable = false)
    private Boolean sanctionsHit = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sanctions_details", columnDefinition = "jsonb")
    private String sanctionsDetails;

    @Enumerated(EnumType.STRING)
    @Column(name = "pep_status", nullable = false, length = 20)
    private PepStatus pepStatus = PepStatus.NOT_CHECKED;

    @Column(name = "pep_verified_at")
    private Instant pepVerifiedAt;

    // ────────────────────────────────────────────────────────────
    // Statut compte
    // ────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus = AccountStatus.PENDING_ACTIVATION;

    @Column(name = "preferred_language", nullable = false, length = 5)
    private String preferredLanguage = "fr";

    // ────────────────────────────────────────────────────────────
    // Consentement (Loi 010-2004/AN)
    // ────────────────────────────────────────────────────────────

    @Column(name = "consent_data_processing", nullable = false)
    private Boolean consentDataProcessing = false;

    @Column(name = "consent_marketing", nullable = false)
    private Boolean consentMarketing = false;

    @Column(name = "consent_data_sharing", nullable = false)
    private Boolean consentDataSharing = false;

    @Column(name = "consent_at")
    private Instant consentAt;

    @Column(name = "consent_ip", length = 45)
    private String consentIp;
}
