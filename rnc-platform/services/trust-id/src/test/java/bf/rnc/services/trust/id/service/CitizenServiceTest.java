package bf.rnc.services.trust.id.service;

import bf.rnc.common.lib.exception.BusinessException;
import bf.rnc.common.security.encryption.HsmEncryptionService;
import bf.rnc.services.trust.id.client.KeycloakAdminClient;
import bf.rnc.services.trust.id.client.SmsOtpService;
import bf.rnc.services.trust.id.dto.KycDecisionRequest;
import bf.rnc.services.trust.id.dto.KycSubmissionRequest;
import bf.rnc.services.trust.id.dto.RegistrationRequest;
import bf.rnc.services.trust.id.dto.RegistrationResponse;
import bf.rnc.services.trust.id.entity.*;
import bf.rnc.services.trust.id.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires du CitizenService — logique métier isolée (sans DB, sans Keycloak réel).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CitizenService — Tests unitaires")
class CitizenServiceTest {

    @Mock private CitizenRepository citizenRepository;
    @Mock private OtpVerificationRepository otpRepository;
    @Mock private KycDocumentRepository kycDocumentRepository;
    @Mock private ConsentHistoryRepository consentHistoryRepository;
    @Mock private SanctionsScreeningRepository sanctionsScreeningRepository;
    @Mock private HashingService hashingService;
    @Mock private OtpCodeGenerator otpGenerator;
    @Mock private HsmEncryptionService encryptionService;
    @Mock private SmsOtpService smsOtpService;
    @Mock private KeycloakAdminClient keycloakClient;
    @Mock private SanctionsScreeningService sanctionsScreeningService;

    @InjectMocks
    private CitizenService citizenService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(citizenService, "otpTtlMinutes", 10);
        ReflectionTestUtils.setField(citizenService, "otpRateLimitMinutes", 1);
        ReflectionTestUtils.setField(citizenService, "otpMaxPerHour", 5);

        // Mocks communs
        when(hashingService.hash(anyString())).thenReturn("hashed-" + Math.abs(System.currentTimeMillis()));
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted");
        when(encryptionService.decrypt(anyString())).thenReturn("decrypted");
        when(otpGenerator.generate6DigitCode()).thenReturn("123456");
        when(otpGenerator.hashOtp(anyString())).thenReturn("otp-hash");
        when(otpGenerator.verifyOtp(anyString(), anyString())).thenReturn(true);
        when(smsOtpService.sendOtp(anyString(), anyString())).thenReturn(true);
        when(keycloakClient.createCitizenUser(anyString(), anyString(), anyString(), any()))
                .thenReturn("keycloak-user-id");
    }

    // ============================================================
    // INSCRIPTION
    // ============================================================

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("✓ Inscription réussie — citoyen créé + OTP envoyé")
        void shouldRegisterCitizenSuccessfully() {
            // Given
            RegistrationRequest req = RegistrationRequest.builder()
                    .phoneNumber("+22670000001")
                    .firstName("Jean")
                    .lastName("Ouédraogo")
                    .gender("M")
                    .region("Centre")
                    .province("Kadiogo")
                    .commune("Ouagadougou")
                    .consentDataProcessing(true)
                    .build();

            when(citizenRepository.existsByPhoneNumberHashAndDeletedFalse(anyString())).thenReturn(false);
            when(citizenRepository.save(any(Citizen.class))).thenAnswer(inv -> {
                Citizen c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });
            when(otpRepository.countRecentOtps(anyString(), any(), any())).thenReturn(0L);

            // When
            RegistrationResponse resp = citizenService.register(req, "127.0.0.1", "JUnit/5");

            // Then
            assertThat(resp).isNotNull();
            assertThat(resp.getCitizenReference()).startsWith("CIT-");
            assertThat(resp.getStatus()).isEqualTo("OTP_SENT");
            assertThat(resp.getOtpExpiresInSeconds()).isEqualTo(600);

            verify(citizenRepository).save(any(Citizen.class));
            verify(smsOtpService).sendOtp(eq("+22670000001"), eq("123456"));
            verify(consentHistoryRepository).save(any(ConsentHistory.class));
        }

        @Test
        @DisplayName("✗ Refus si pas de consentement (Loi 010-2004/AN)")
        void shouldRejectWithoutConsent() {
            RegistrationRequest req = RegistrationRequest.builder()
                    .phoneNumber("+22670000001")
                    .firstName("Test")
                    .lastName("User")
                    .region("Centre")
                    .province("Kadiogo")
                    .commune("Ouagadougou")
                    .consentDataProcessing(false)
                    .build();

            assertThatThrownBy(() -> citizenService.register(req, "ip", "ua"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("consentement");

            verifyNoInteractions(citizenRepository);
        }

        @Test
        @DisplayName("✗ Refus si téléphone déjà enregistré")
        void shouldRejectDuplicatePhone() {
            RegistrationRequest req = RegistrationRequest.builder()
                    .phoneNumber("+22670000001")
                    .firstName("Test")
                    .lastName("User")
                    .region("Centre")
                    .province("Kadiogo")
                    .commune("Ouagadougou")
                    .consentDataProcessing(true)
                    .build();

            when(citizenRepository.existsByPhoneNumberHashAndDeletedFalse(anyString())).thenReturn(true);

            assertThatThrownBy(() -> citizenService.register(req, "ip", "ua"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("déjà enregistré");
        }

        @Test
        @DisplayName("✗ Rate limit : refus si OTP envoyé dans la dernière minute")
        void shouldRateLimitOtp() {
            RegistrationRequest req = RegistrationRequest.builder()
                    .phoneNumber("+22670000001")
                    .firstName("Test")
                    .lastName("User")
                    .region("Centre")
                    .province("Kadiogo")
                    .commune("Ouagadougou")
                    .consentDataProcessing(true)
                    .build();

            when(citizenRepository.existsByPhoneNumberHashAndDeletedFalse(anyString())).thenReturn(false);
            when(citizenRepository.save(any())).thenAnswer(inv -> {
                Citizen c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });
            when(otpRepository.countRecentOtps(anyString(), any(), any())).thenReturn(1L);

            assertThatThrownBy(() -> citizenService.register(req, "ip", "ua"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("attendre");
        }
    }

    // ============================================================
    // KYC
    // ============================================================

    @Nested
    @DisplayName("submitKyc()")
    class SubmitKyc {

        @Test
        @DisplayName("✓ Soumission KYC réussie")
        void shouldSubmitKyc() {
            Citizen citizen = buildActiveCitizen();
            when(citizenRepository.findByCitizenReferenceAndDeletedFalse("CIT-ABC123"))
                    .thenReturn(Optional.of(citizen));
            when(citizenRepository.existsByCinNumberHashAndDeletedFalse(anyString())).thenReturn(false);
            when(citizenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            KycSubmissionRequest req = KycSubmissionRequest.builder()
                    .cinNumber("B123456")
                    .cinIssueDate(LocalDate.of(2020, 1, 15))
                    .cinIssuePlace("Ouagadougou")
                    .build();

            var result = citizenService.submitKyc("CIT-ABC123", req);

            assertThat(result.getKycStatus()).isEqualTo(KycStatus.SUBMITTED);
            verify(citizenRepository).save(any(Citizen.class));
        }

        @Test
        @DisplayName("✗ Refus KYC si téléphone non vérifié")
        void shouldRejectKycIfPhoneNotVerified() {
            Citizen citizen = buildActiveCitizen();
            citizen.setPhoneNumberVerified(false);
            citizen.setAccountStatus(AccountStatus.PENDING_ACTIVATION);

            when(citizenRepository.findByCitizenReferenceAndDeletedFalse("CIT-ABC123"))
                    .thenReturn(Optional.of(citizen));

            KycSubmissionRequest req = KycSubmissionRequest.builder()
                    .cinNumber("B123456")
                    .cinIssuePlace("Ouagadougou")
                    .build();

            assertThatThrownBy(() -> citizenService.submitKyc("CIT-ABC123", req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("téléphone");
        }

        @Test
        @DisplayName("✗ Refus KYC si CIN déjà utilisée")
        void shouldRejectDuplicateCin() {
            Citizen citizen = buildActiveCitizen();
            when(citizenRepository.findByCitizenReferenceAndDeletedFalse("CIT-ABC123"))
                    .thenReturn(Optional.of(citizen));
            when(citizenRepository.existsByCinNumberHashAndDeletedFalse(anyString())).thenReturn(true);

            KycSubmissionRequest req = KycSubmissionRequest.builder()
                    .cinNumber("B123456")
                    .cinIssuePlace("Ouagadougou")
                    .build();

            assertThatThrownBy(() -> citizenService.submitKyc("CIT-ABC123", req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("déjà enregistrée");
        }
    }

    @Nested
    @DisplayName("validateKyc()")
    class ValidateKyc {

        @Test
        @DisplayName("✓ Validation KYC par agent")
        void shouldApproveKyc() {
            Citizen citizen = buildActiveCitizen();
            citizen.setKycStatus(KycStatus.SUBMITTED);

            when(citizenRepository.findById(citizen.getId())).thenReturn(Optional.of(citizen));
            when(citizenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            KycDecisionRequest req = KycDecisionRequest.builder()
                    .citizenId(citizen.getId())
                    .approved(true)
                    .build();

            var result = citizenService.validateKyc(req, "agent-001");

            assertThat(result.getKycStatus()).isEqualTo(KycStatus.VERIFIED);
            assertThat(result.getKycVerifiedAt()).isNotNull();
        }

        @Test
        @DisplayName("✗ Rejet KYC sans raison")
        void shouldRejectWithoutReason() {
            Citizen citizen = buildActiveCitizen();
            citizen.setKycStatus(KycStatus.SUBMITTED);

            when(citizenRepository.findById(citizen.getId())).thenReturn(Optional.of(citizen));

            KycDecisionRequest req = KycDecisionRequest.builder()
                    .citizenId(citizen.getId())
                    .approved(false)
                    .build();

            assertThatThrownBy(() -> citizenService.validateKyc(req, "agent-001"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("raison");
        }
    }

    // ============================================================
    // ADMIN
    // ============================================================

    @Nested
    @DisplayName("Admin actions")
    class AdminActions {

        @Test
        @DisplayName("✓ Suspension compte")
        void shouldSuspendAccount() {
            Citizen citizen = buildActiveCitizen();
            when(citizenRepository.findById(citizen.getId())).thenReturn(Optional.of(citizen));
            when(citizenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            citizenService.suspendAccount(citizen.getId(), "fraude suspectée");

            assertThat(citizen.getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
            verify(keycloakClient).disableUser("keycloak-user-id");
        }

        @Test
        @DisplayName("✓ Clôture compte — anonymise les données (droit à l'oubli)")
        void shouldCloseAndAnonymize() {
            Citizen citizen = buildActiveCitizen();
            when(citizenRepository.findById(citizen.getId())).thenReturn(Optional.of(citizen));
            when(citizenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            citizenService.closeAccount(citizen.getId(), "demande utilisateur");

            assertThat(citizen.getAccountStatus()).isEqualTo(AccountStatus.CLOSED);
            assertThat(citizen.isDeleted()).isTrue();
            assertThat(citizen.getPhoneNumberEncrypted()).isEqualTo("[CLOSED]");
            assertThat(citizen.getFirstNameEncrypted()).isEqualTo("[CLOSED]");
            assertThat(citizen.getLastNameEncrypted()).isEqualTo("[CLOSED]");
            assertThat(citizen.getCinNumberEncrypted()).isNull();
        }

        @Test
        @DisplayName("✗ Réactivation refusée si KYC non vérifié")
        void shouldNotReactivateWithoutKyc() {
            Citizen citizen = buildActiveCitizen();
            citizen.setKycStatus(KycStatus.PENDING);
            citizen.setAccountStatus(AccountStatus.SUSPENDED);
            when(citizenRepository.findById(citizen.getId())).thenReturn(Optional.of(citizen));

            assertThatThrownBy(() -> citizenService.reactivateAccount(citizen.getId()))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    private Citizen buildActiveCitizen() {
        Citizen c = new Citizen();
        c.setId(UUID.randomUUID());
        c.setCitizenReference("CIT-ABC123DEF456");
        c.setKeycloakUserId("keycloak-user-id");
        c.setPhoneNumberEncrypted("encrypted");
        c.setPhoneNumberHash("hashed");
        c.setPhoneNumberVerified(true);
        c.setFirstNameEncrypted("encrypted");
        c.setLastNameEncrypted("encrypted");
        c.setRegion("Centre");
        c.setProvince("Kadiogo");
        c.setCommune("Ouagadougou");
        c.setKycStatus(KycStatus.PENDING);
        c.setAccountStatus(AccountStatus.ACTIVE);
        c.setConsentDataProcessing(true);
        c.setCreatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        c.setUpdatedAt(Instant.now());
        return c;
    }
}
