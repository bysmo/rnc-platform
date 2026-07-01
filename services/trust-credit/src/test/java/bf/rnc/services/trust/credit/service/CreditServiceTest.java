package bf.rnc.services.trust.credit.service;

import bf.rnc.common.lib.exception.BusinessException;
import bf.rnc.common.lib.exception.NotFoundException;
import bf.rnc.services.trust.credit.dto.CreditDecisionRequest;
import bf.rnc.services.trust.credit.dto.CreditRequest;
import bf.rnc.services.trust.credit.dto.PaymentRequest;
import bf.rnc.services.trust.credit.entity.*;
import bf.rnc.services.trust.credit.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires du CreditService — logique métier isolée.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CreditService — Tests unitaires")
class CreditServiceTest {

    @Mock private CreditRepository creditRepository;
    @Mock private RepaymentInstallmentRepository installmentRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private CreditEventRepository creditEventRepository;

    @InjectMocks
    private CreditService creditService;

    // ============================================================
    // DEMANDE
    // ============================================================

    @Nested
    @DisplayName("requestCredit()")
    class RequestCredit {

        @Test
        @DisplayName("✓ Demande valide réussie")
        void shouldCreateCreditRequest() {
            CreditRequest req = CreditRequest.builder()
                    .citizenId("citizen-001")
                    .amountXof(50_000L)
                    .durationDays(90)
                    .purpose(CreditPurpose.MERCHANT)
                    .build();

            when(creditRepository.countActiveCreditsForCitizen(eq("citizen-001"), anyList())).thenReturn(0L);
            when(creditRepository.totalOutstandingForCitizen(eq("citizen-001"), anyList())).thenReturn(0L);
            when(creditRepository.save(any(Credit.class))).thenAnswer(inv -> {
                Credit c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });

            Credit result = creditService.requestCredit(req);

            assertThat(result.getStatus()).isEqualTo(CreditStatus.REQUESTED);
            assertThat(result.getCreditReference()).startsWith("CR-");
            assertThat(result.getAmountMinor()).isEqualTo(50_000L);
            verify(creditEventRepository).save(any(CreditEvent.class));
        }

        @Test
        @DisplayName("✗ Refus si montant < 5000 XOF")
        void shouldRejectAmountTooLow() {
            CreditRequest req = CreditRequest.builder()
                    .citizenId("citizen-001")
                    .amountXof(3000L)
                    .durationDays(30)
                    .purpose(CreditPurpose.OTHER)
                    .build();

            assertThatThrownBy(() -> creditService.requestCredit(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("5 000 XOF");
        }

        @Test
        @DisplayName("✗ Refus si durée < 7 jours")
        void shouldRejectDurationTooShort() {
            CreditRequest req = CreditRequest.builder()
                    .citizenId("citizen-001")
                    .amountXof(10_000L)
                    .durationDays(3)
                    .purpose(CreditPurpose.EMERGENCY)
                    .build();

            assertThatThrownBy(() -> creditService.requestCredit(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("7 jours");
        }

        @Test
        @DisplayName("✗ Refus si 3 crédits actifs déjà")
        void shouldRejectMaxActiveCredits() {
            CreditRequest req = CreditRequest.builder()
                    .citizenId("citizen-001")
                    .amountXof(10_000L)
                    .durationDays(30)
                    .purpose(CreditPurpose.MERCHANT)
                    .build();

            when(creditRepository.countActiveCreditsForCitizen(any(), anyList())).thenReturn(3L);

            assertThatThrownBy(() -> creditService.requestCredit(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("3 crédits");
        }

        @Test
        @DisplayName("✗ Refus si encours total dépasse 500 000 XOF")
        void shouldRejectMaxOutstandingExceeded() {
            CreditRequest req = CreditRequest.builder()
                    .citizenId("citizen-001")
                    .amountXof(100_000L)
                    .durationDays(90)
                    .purpose(CreditPurpose.FARMING)
                    .build();

            when(creditRepository.countActiveCreditsForCitizen(any(), anyList())).thenReturn(1L);
            when(creditRepository.totalOutstandingForCitizen(any(), anyList())).thenReturn(450_000L);

            assertThatThrownBy(() -> creditService.requestCredit(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("500 000");
        }
    }

    // ============================================================
    // APPROBATION
    // ============================================================

    @Nested
    @DisplayName("approve()")
    class Approve {

        @Test
        @DisplayName("✓ Approbation réussie génère échéancier")
        void shouldApproveAndGenerateSchedule() {
            Credit credit = buildCredit(CreditStatus.REQUESTED, 50_000L, 90);
            CreditDecisionRequest decision = CreditDecisionRequest.builder()
                    .approved(true)
                    .escrowAccountId("ESC-001")
                    .trustScore(700)
                    .build();

            when(creditRepository.findById(credit.getId())).thenReturn(Optional.of(credit));
            when(creditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Credit result = creditService.approve(credit.getId(), decision, "agent-001");

            assertThat(result.getStatus()).isEqualTo(CreditStatus.APPROVED);
            assertThat(result.getMaturityDate()).isNotNull();
            assertThat(result.getEscrowAccountId()).isEqualTo("ESC-001");
            // 90 jours / 30 = 3 échéances
            verify(installmentRepository, times(3)).save(any(RepaymentInstallment.class));
        }

        @Test
        @DisplayName("✗ Approbation refusée sans escrow")
        void shouldRejectApprovalWithoutEscrow() {
            Credit credit = buildCredit(CreditStatus.REQUESTED, 50_000L, 90);
            CreditDecisionRequest decision = CreditDecisionRequest.builder()
                    .approved(true)
                    .build();

            when(creditRepository.findById(credit.getId())).thenReturn(Optional.of(credit));

            assertThatThrownBy(() -> creditService.approve(credit.getId(), decision, "agent"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("escrow");
        }

        @Test
        @DisplayName("✗ Rejet refusé sans raison")
        void shouldRejectRejectionWithoutReason() {
            Credit credit = buildCredit(CreditStatus.REQUESTED, 50_000L, 90);
            CreditDecisionRequest decision = CreditDecisionRequest.builder()
                    .approved(false)
                    .build();

            when(creditRepository.findById(credit.getId())).thenReturn(Optional.of(credit));

            assertThatThrownBy(() -> creditService.approve(credit.getId(), decision, "agent"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("raison");
        }
    }

    // ============================================================
    // PAIEMENT
    // ============================================================

    @Nested
    @DisplayName("recordPayment()")
    class RecordPayment {

        @Test
        @DisplayName("✓ Paiement idempotent — deuxième appel ignoré")
        void shouldBeIdempotent() {
            Payment existing = new Payment();
            existing.setId(UUID.randomUUID());
            existing.setIdempotencyKey("KEY-001");

            when(paymentRepository.findByIdempotencyKey("KEY-001")).thenReturn(Optional.of(existing));

            PaymentRequest req = PaymentRequest.builder()
                    .creditId(UUID.randomUUID())
                    .amountXof(10_000L)
                    .paymentChannel(PaymentChannel.MOBILE_MONEY)
                    .idempotencyKey("KEY-001")
                    .build();

            Payment result = creditService.recordPayment(req);

            assertThat(result.getId()).isEqualTo(existing.getId());
            verify(creditRepository, never()).findById(any());
        }

        @Test
        @DisplayName("✗ Paiement refusé si crédit non actif")
        void shouldRejectPaymentForNonActiveCredit() {
            Credit credit = buildCredit(CreditStatus.REQUESTED, 50_000L, 90);

            when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
            when(creditRepository.findById(credit.getId())).thenReturn(Optional.of(credit));

            PaymentRequest req = PaymentRequest.builder()
                    .creditId(credit.getId())
                    .amountXof(10_000L)
                    .paymentChannel(PaymentChannel.MOBILE_MONEY)
                    .idempotencyKey("KEY-002")
                    .build();

            assertThatThrownBy(() -> creditService.recordPayment(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("actif");
        }
    }

    // ============================================================
    // CANCEL
    // ============================================================

    @Test
    @DisplayName("Annulation crédit — refus si déjà disbursed")
    void shouldNotCancelDisbursedCredit() {
        Credit credit = buildCredit(CreditStatus.ACTIVE, 50_000L, 90);
        when(creditRepository.findById(credit.getId())).thenReturn(Optional.of(credit));

        assertThatThrownBy(() -> creditService.cancel(credit.getId(), "test", "admin"))
                .isInstanceOf(BusinessException.class);
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private Credit buildCredit(CreditStatus status, long amount, int duration) {
        Credit c = new Credit();
        c.setId(UUID.randomUUID());
        c.setCitizenId("citizen-001");
        c.setCreditReference("CR-TEST123");
        c.setAmountMinor(amount);
        c.setDurationDays(duration);
        c.setPurpose(CreditPurpose.MERCHANT);
        c.setStatus(status);
        c.setInterestRateBps(0);
        return c;
    }
}
