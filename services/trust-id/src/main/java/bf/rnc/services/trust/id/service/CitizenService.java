package bf.rnc.services.trust.id.service;

import bf.rnc.common.lib.exception.BusinessException;
import bf.rnc.common.lib.exception.NotFoundException;
import bf.rnc.common.lib.util.IdGenerator;
import bf.rnc.common.security.audit.Auditable;
import bf.rnc.common.security.encryption.HsmEncryptionService;
import bf.rnc.services.trust.id.client.KeycloakAdminClient;
import bf.rnc.services.trust.id.client.SmsOtpService;
import bf.rnc.services.trust.id.dto.*;
import bf.rnc.services.trust.id.entity.*;
import bf.rnc.services.trust.id.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Service métier central de Trust ID : inscription, OTP, KYC, gestion profil.
 *
 * <p>Cycle de vie d'un citoyen :</p>
 * <ol>
 *   <li>registration() — crée le citoyen (PENDING_ACTIVATION), envoie OTP</li>
 *   <li>verifyOtp() — vérifie l'OTP, active le compte, crée user Keycloak</li>
 *   <li>submitKyc() — citoyen soumet sa CIN</li>
 *   <li>validateKyc() — agent valide (ou rejette) le KYC</li>
 *   <li>getProfile() — consultation publique</li>
 *   <li>suspendAccount() / closeAccount() — gestion admin</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CitizenService {

    private final CitizenRepository citizenRepository;
    private final OtpVerificationRepository otpRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final ConsentHistoryRepository consentHistoryRepository;
    private final SanctionsScreeningRepository sanctionsScreeningRepository;

    private final HashingService hashingService;
    private final OtpCodeGenerator otpGenerator;
    private final HsmEncryptionService encryptionService;
    private final SmsOtpService smsOtpService;
    private final KeycloakAdminClient keycloakClient;
    private final SanctionsScreeningService sanctionsScreeningService;

    @Value("${rnc.otp.ttl-minutes:10}")
    private int otpTtlMinutes;

    @Value("${rnc.otp.rate-limit.minutes:1}")
    private int otpRateLimitMinutes;

    @Value("${rnc.otp.rate-limit.max-per-hour:5}")
    private int otpMaxPerHour;

    // ============================================================
    // INSCRIPTION
    // ============================================================

    @Auditable(action = "REGISTER_CITIZEN", resourceType = "Citizen", actorType = "CITIZEN", extractResourceId = true)
    @Transactional
    public RegistrationResponse register(RegistrationRequest request, String ipAddress, String userAgent) {
        // 1. Validation : consentement obligatoire (Loi 010-2004/AN)
        if (!Boolean.TRUE.equals(request.getConsentDataProcessing())) {
            throw new BusinessException("CONSENT_REQUIRED",
                "Le consentement au traitement des données est obligatoire (Loi 010-2004/AN)");
        }

        // 2. Validation : numéro de téléphone unique
        String phoneHash = hashingService.hash(request.getPhoneNumber());
        if (citizenRepository.existsByPhoneNumberHashAndDeletedFalse(phoneHash)) {
            throw new BusinessException("PHONE_ALREADY_REGISTERED",
                "Ce numéro de téléphone est déjà enregistré. Connectez-vous ou utilisez un autre numéro.");
        }

        // 3. Création du citoyen
        Citizen citizen = new Citizen();
        citizen.setCitizenReference(IdGenerator.prefixed("CIT", 12));
        citizen.setPhoneNumberEncrypted(encryptionService.encrypt(request.getPhoneNumber()));
        citizen.setPhoneNumberHash(phoneHash);
        citizen.setPhoneNumberVerified(false);
        citizen.setFirstNameEncrypted(encryptionService.encrypt(request.getFirstName()));
        citizen.setLastNameEncrypted(encryptionService.encrypt(request.getLastName()));
        citizen.setGender(request.getGender());
        citizen.setRegion(request.getRegion());
        citizen.setProvince(request.getProvince());
        citizen.setCommune(request.getCommune());
        citizen.setVillage(request.getVillage());

        citizen.setKycStatus(KycStatus.PENDING);
        citizen.setAccountStatus(AccountStatus.PENDING_ACTIVATION);
        citizen.setPreferredLanguage(request.getPreferredLanguage());

        citizen.setConsentDataProcessing(true);
        citizen.setConsentMarketing(Boolean.TRUE.equals(request.getConsentMarketing()));
        citizen.setConsentDataSharing(Boolean.TRUE.equals(request.getConsentDataSharing()));
        citizen.setConsentAt(Instant.now());
        citizen.setConsentIp(ipAddress);

        citizen = citizenRepository.save(citizen);

        // 4. Enregistrement de l'historique de consentement (preuve Loi 010-2004/AN)
        saveConsentHistory(citizen.getId(), ConsentType.DATA_PROCESSING, true, ipAddress, userAgent);
        if (Boolean.TRUE.equals(request.getConsentMarketing())) {
            saveConsentHistory(citizen.getId(), ConsentType.MARKETING, true, ipAddress, userAgent);
        }
        if (Boolean.TRUE.equals(request.getConsentDataSharing())) {
            saveConsentHistory(citizen.getId(), ConsentType.DATA_SHARING, true, ipAddress, userAgent);
        }

        // 5. Génération + envoi OTP
        sendOtpInternal(citizen, phoneHash, OtpPurpose.REGISTRATION, ipAddress, userAgent);

        log.info("Citoyen inscrit: reference={}, phone={}, region={}",
                citizen.getCitizenReference(), maskPhone(request.getPhoneNumber()), citizen.getRegion());

        return RegistrationResponse.builder()
                .citizenReference(citizen.getCitizenReference())
                .status("OTP_SENT")
                .message("Un code OTP a été envoyé par SMS au " + maskPhone(request.getPhoneNumber()))
                .otpExpiresInSeconds(otpTtlMinutes * 60)
                .build();
    }

    // ============================================================
    // OTP — VÉRIFICATION
    // ============================================================

    @Auditable(action = "VERIFY_OTP", resourceType = "Citizen", actorType = "CITIZEN")
    @Transactional
    public CitizenProfileResponse verifyOtp(OtpVerificationRequest request, String ipAddress, String userAgent) {
        Citizen citizen = citizenRepository.findByCitizenReferenceAndDeletedFalse(request.getCitizenReference())
                .orElseThrow(() -> new NotFoundException("Citizen", request.getCitizenReference()));

        if (citizen.getPhoneNumberVerified()) {
            throw new BusinessException("ALREADY_VERIFIED",
                "Ce numéro est déjà vérifié");
        }

        OtpVerification otp = otpRepository.findLatestActiveOtp(
                citizen.getPhoneNumberHash(),
                OtpPurpose.REGISTRATION,
                Instant.now()
        ).orElseThrow(() -> new BusinessException("OTP_NOT_FOUND",
            "Aucun OTP actif. Demandez un nouvel OTP."));

        // Incrémente les tentatives
        otp.setAttempts(otp.getAttempts() + 1);
        otpRepository.save(otp);

        if (otp.getAttempts() > otp.getMaxAttempts()) {
            log.warn("OTP bloqué (trop de tentatives) — citizen={}", citizen.getCitizenReference());
            throw new BusinessException("OTP_BLOCKED",
                "Trop de tentatives. Demandez un nouvel OTP.");
        }

        // Vérification du code
        if (!otpGenerator.verifyOtp(request.getOtpCode(), otp.getOtpCodeHash())) {
            throw new BusinessException("OTP_INVALID",
                "Code OTP incorrect. Il vous reste " + (otp.getMaxAttempts() - otp.getAttempts()) + " tentative(s).");
        }

        // Marquer OTP comme vérifié + consommé
        otp.setVerified(true);
        otp.setConsumedAt(Instant.now());
        otpRepository.save(otp);

        // Activer le citoyen
        citizen.setPhoneNumberVerified(true);
        citizen.setAccountStatus(AccountStatus.ACTIVE);

        // Créer l'utilisateur Keycloak
        try {
            String keycloakUserId = keycloakClient.createCitizenUser(
                    decrypt(citizen.getPhoneNumberEncrypted()),
                    decrypt(citizen.getFirstNameEncrypted()),
                    decrypt(citizen.getLastNameEncrypted()),
                    citizen.getEmailEncrypted() != null ? decrypt(citizen.getEmailEncrypted()) : null
            );
            citizen.setKeycloakUserId(keycloakUserId);
            keycloakClient.assignRole(keycloakUserId, "CITIZEN");
        } catch (Exception e) {
            log.error("Échec création Keycloak pour {} — compte activé sans IAM",
                    citizen.getCitizenReference(), e);
        }

        citizen = citizenRepository.save(citizen);

        // Screening sanctions automatique (asynchrone en prod, synchrone au MVP)
        try {
            sanctionsScreeningService.screenCitizen(citizen);
        } catch (Exception e) {
            log.warn("Échec screening sanctions pour {} (non bloquant)", citizen.getCitizenReference(), e);
        }

        log.info("Citoyen activé: reference={}", citizen.getCitizenReference());
        return toProfileResponse(citizen);
    }

    // ============================================================
    // OTP — RENVOI
    // ============================================================

    @Auditable(action = "RESEND_OTP", resourceType = "Citizen", actorType = "CITIZEN")
    @Transactional
    public void resendOtp(String citizenReference, String ipAddress, String userAgent) {
        Citizen citizen = citizenRepository.findByCitizenReferenceAndDeletedFalse(citizenReference)
                .orElseThrow(() -> new NotFoundException("Citizen", citizenReference));

        if (citizen.getPhoneNumberVerified()) {
            throw new BusinessException("ALREADY_VERIFIED", "Numéro déjà vérifié");
        }

        // Rate limiting : max N OTP par heure
        long recentOtps = otpRepository.countRecentOtps(
                citizen.getPhoneNumberHash(),
                OtpPurpose.REGISTRATION,
                Instant.now().minus(1, ChronoUnit.HOURS));
        if (recentOtps >= otpMaxPerHour) {
            throw new BusinessException("OTP_RATE_LIMIT",
                "Trop de demandes OTP. Réessayez dans une heure.");
        }

        sendOtpInternal(citizen, citizen.getPhoneNumberHash(), OtpPurpose.REGISTRATION, ipAddress, userAgent);
    }

    // ============================================================
    // KYC
    // ============================================================

    @Auditable(action = "SUBMIT_KYC", resourceType = "Citizen", actorType = "CITIZEN")
    @Transactional
    public CitizenProfileResponse submitKyc(String citizenReference, KycSubmissionRequest request) {
        Citizen citizen = citizenRepository.findByCitizenReferenceAndDeletedFalse(citizenReference)
                .orElseThrow(() -> new NotFoundException("Citizen", citizenReference));

        if (!citizen.getPhoneNumberVerified()) {
            throw new BusinessException("PHONE_NOT_VERIFIED",
                "Vous devez vérifier votre numéro de téléphone avant de soumettre le KYC");
        }

        if (citizen.getKycStatus() == KycStatus.VERIFIED) {
            throw new BusinessException("KYC_ALREADY_VERIFIED", "KYC déjà validé");
        }

        // Vérifier CIN unique
        String cinHash = hashingService.hash(request.getCinNumber());
        if (citizenRepository.existsByCinNumberHashAndDeletedFalse(cinHash) &&
                !citizen.getCinNumberHash().equals(cinHash)) {
            throw new BusinessException("CIN_ALREADY_USED",
                "Cette CIN est déjà enregistrée pour un autre compte");
        }

        // Mettre à jour CIN
        citizen.setCinNumberEncrypted(encryptionService.encrypt(request.getCinNumber()));
        citizen.setCinNumberHash(cinHash);
        citizen.setCinIssueDate(request.getCinIssueDate());
        citizen.setCinIssuePlace(request.getCinIssuePlace());

        if (request.getDateOfBirth() != null) {
            citizen.setDateOfBirthEncrypted(encryptionService.encrypt(request.getDateOfBirth().toString()));
        }

        citizen.setKycStatus(KycStatus.SUBMITTED);
        citizen.setKycRejectionReason(null);
        citizen = citizenRepository.save(citizen);

        log.info("KYC soumis pour {} — en attente de validation agent", citizenReference);
        return toProfileResponse(citizen);
    }

    @Auditable(action = "VALIDATE_KYC", resourceType = "Citizen", actorType = "ADMIN")
    @Transactional
    public CitizenProfileResponse validateKyc(KycDecisionRequest request, String agentId) {
        Citizen citizen = citizenRepository.findById(request.getCitizenId())
                .orElseThrow(() -> new NotFoundException("Citizen", request.getCitizenId().toString()));

        if (citizen.getKycStatus() != KycStatus.SUBMITTED) {
            throw new BusinessException("INVALID_KYC_STATE",
                "Le KYC doit être en statut SUBMITTED pour être validé");
        }

        if (request.getApproved()) {
            citizen.setKycStatus(KycStatus.VERIFIED);
            citizen.setKycVerifiedAt(Instant.now());
            citizen.setKycVerifiedBy(agentId);
            citizen.setKycRejectionReason(null);
            log.info("KYC APPROVED pour {} par {}", citizen.getCitizenReference(), agentId);
        } else {
            if (request.getRejectionReason() == null || request.getRejectionReason().isBlank()) {
                throw new BusinessException("REJECTION_REASON_REQUIRED",
                    "La raison du rejet est obligatoire");
            }
            citizen.setKycStatus(KycStatus.REJECTED);
            citizen.setKycRejectionReason(request.getRejectionReason());
            citizen.setKycVerifiedBy(agentId);
            log.info("KYC REJECTED pour {} par {} — raison: {}",
                    citizen.getCitizenReference(), agentId, request.getRejectionReason());
        }

        citizen = citizenRepository.save(citizen);
        return toProfileResponse(citizen);
    }

    // ============================================================
    // CONSULTATION
    // ============================================================

    @Transactional(readOnly = true)
    public CitizenProfileResponse getProfile(String citizenReference) {
        Citizen citizen = citizenRepository.findByCitizenReferenceAndDeletedFalse(citizenReference)
                .orElseThrow(() -> new NotFoundException("Citizen", citizenReference));
        return toProfileResponse(citizen);
    }

    @Transactional(readOnly = true)
    public Citizen getCitizenEntity(UUID citizenId) {
        return citizenRepository.findById(citizenId)
                .orElseThrow(() -> new NotFoundException("Citizen", citizenId.toString()));
    }

    @Transactional(readOnly = true)
    public Citizen getCitizenByReference(String citizenReference) {
        return citizenRepository.findByCitizenReferenceAndDeletedFalse(citizenReference)
                .orElseThrow(() -> new NotFoundException("Citizen", citizenReference));
    }

    // ============================================================
    // ADMIN
    // ============================================================

    @Auditable(action = "SUSPEND_CITIZEN", resourceType = "Citizen", actorType = "ADMIN")
    @Transactional
    public void suspendAccount(UUID citizenId, String reason) {
        Citizen citizen = getCitizenEntity(citizenId);
        citizen.setAccountStatus(AccountStatus.SUSPENDED);
        citizenRepository.save(citizen);
        if (citizen.getKeycloakUserId() != null) {
            keycloakClient.disableUser(citizen.getKeycloakUserId());
        }
        log.warn("Compte suspendu: {} — raison: {}", citizen.getCitizenReference(), reason);
    }

    @Auditable(action = "REACTIVATE_CITIZEN", resourceType = "Citizen", actorType = "ADMIN")
    @Transactional
    public void reactivateAccount(UUID citizenId) {
        Citizen citizen = getCitizenEntity(citizenId);
        if (citizen.getKycStatus() != KycStatus.VERIFIED) {
            throw new BusinessException("CANNOT_REACTIVATE",
                "Le KYC doit être vérifié pour réactiver le compte");
        }
        citizen.setAccountStatus(AccountStatus.ACTIVE);
        citizenRepository.save(citizen);
        if (citizen.getKeycloakUserId() != null) {
            keycloakClient.activateUser(citizen.getKeycloakUserId());
        }
        log.info("Compte réactivé: {}", citizen.getCitizenReference());
    }

    @Auditable(action = "CLOSE_CITIZEN", resourceType = "Citizen", actorType = "ADMIN")
    @Transactional
    public void closeAccount(UUID citizenId, String reason) {
        Citizen citizen = getCitizenEntity(citizenId);
        // Anonymisation des données personnelles (droit à l'oubli — Loi 010-2004/AN)
        citizen.setAccountStatus(AccountStatus.CLOSED);
        citizen.setDeleted(true);
        citizen.setPhoneNumberEncrypted("[CLOSED]");
        citizen.setPhoneNumberHash("[CLOSED-" + citizen.getId() + "]");
        citizen.setFirstNameEncrypted("[CLOSED]");
        citizen.setLastNameEncrypted("[CLOSED]");
        citizen.setEmailEncrypted(null);
        citizen.setEmailHash(null);
        citizen.setCinNumberEncrypted(null);
        citizen.setCinNumberHash(null);
        citizen.setAddressEncrypted(null);

        if (citizen.getKeycloakUserId() != null) {
            keycloakClient.disableUser(citizen.getKeycloakUserId());
        }

        citizenRepository.save(citizen);
        log.warn("Compte clôturé et anonymisé: {} — raison: {}", citizen.getCitizenReference(), reason);
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private void sendOtpInternal(Citizen citizen, String phoneHash, OtpPurpose purpose,
                                  String ipAddress, String userAgent) {
        // Rate limiting court terme (1 OTP / minute)
        long recentOtps = otpRepository.countRecentOtps(
                phoneHash, purpose, Instant.now().minus(otpRateLimitMinutes, ChronoUnit.MINUTES));
        if (recentOtps > 0) {
            throw new BusinessException("OTP_RATE_LIMIT_SHORT",
                "Veuillez attendre " + otpRateLimitMinutes + " minute(s) avant de demander un nouvel OTP");
        }

        String otpCode = otpGenerator.generate6DigitCode();
        String otpHash = otpGenerator.hashOtp(otpCode);

        OtpVerification otp = new OtpVerification();
        otp.setPhoneNumberHash(phoneHash);
        otp.setOtpCodeHash(otpHash);
        otp.setPurpose(purpose);
        otp.setExpiresAt(Instant.now().plus(otpTtlMinutes, ChronoUnit.MINUTES));
        otp.setIpAddress(ipAddress);
        otp.setUserAgent(userAgent);
        otpRepository.save(otp);

        // Envoi SMS
        String phone = decrypt(citizen.getPhoneNumberEncrypted());
        boolean sent = smsOtpService.sendOtp(phone, otpCode);
        if (!sent) {
            log.error("Échec envoi SMS OTP pour {} — OTP en base mais non délivré",
                    citizen.getCitizenReference());
            throw new BusinessException("SMS_SEND_FAILED",
                "Échec d'envoi du SMS. Réessayez dans quelques instants.");
        }
    }

    private void saveConsentHistory(UUID citizenId, ConsentType type, boolean granted,
                                     String ip, String userAgent) {
        ConsentHistory ch = new ConsentHistory();
        ch.setCitizenId(citizenId);
        ch.setConsentType(type);
        ch.setGranted(granted);
        ch.setIpAddress(ip);
        ch.setUserAgent(userAgent);
        consentHistoryRepository.save(ch);
    }

    private String decrypt(String encrypted) {
        try {
            return encryptionService.decrypt(encrypted);
        } catch (Exception e) {
            log.error("Erreur déchiffrement", e);
            return "[DECRYPT_ERROR]";
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 8) return phone;
        return phone.substring(0, 6) + " ** ** ** " + phone.substring(phone.length() - 2);
    }

    private CitizenProfileResponse toProfileResponse(Citizen citizen) {
        return CitizenProfileResponse.builder()
                .id(citizen.getId())
                .citizenReference(citizen.getCitizenReference())
                .phoneNumberMasked(citizen.getPhoneNumberEncrypted() != null
                        && !"[CLOSED]".equals(citizen.getPhoneNumberEncrypted())
                        ? maskPhone(decrypt(citizen.getPhoneNumberEncrypted())) : null)
                .firstName(citizen.getFirstNameEncrypted() != null
                        && !"[CLOSED]".equals(citizen.getFirstNameEncrypted())
                        ? decrypt(citizen.getFirstNameEncrypted()) : null)
                .lastName(citizen.getLastNameEncrypted() != null
                        && !"[CLOSED]".equals(citizen.getLastNameEncrypted())
                        ? decrypt(citizen.getLastNameEncrypted()) : null)
                .gender(citizen.getGender())
                .region(citizen.getRegion())
                .province(citizen.getProvince())
                .commune(citizen.getCommune())
                .village(citizen.getVillage())
                .kycStatus(citizen.getKycStatus())
                .kycVerifiedAt(citizen.getKycVerifiedAt())
                .sanctionsScreened(citizen.getSanctionsScreened())
                .sanctionsHit(citizen.getSanctionsHit())
                .pepStatus(citizen.getPepStatus())
                .accountStatus(citizen.getAccountStatus())
                .phoneVerified(citizen.getPhoneNumberVerified())
                .emailVerified(citizen.getEmailVerified())
                .consentDataProcessing(citizen.getConsentDataProcessing())
                .consentMarketing(citizen.getConsentMarketing())
                .consentDataSharing(citizen.getConsentDataSharing())
                .createdAt(citizen.getCreatedAt())
                .updatedAt(citizen.getUpdatedAt())
                .build();
    }
}
