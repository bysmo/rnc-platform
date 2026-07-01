package bf.rnc.services.trust.credit.integration;

import bf.rnc.common.lib.exception.BusinessException;
import bf.rnc.services.trust.credit.dto.CreditDecisionRequest;
import bf.rnc.services.trust.credit.dto.CreditRequest;
import bf.rnc.services.trust.credit.dto.InstallmentResponse;
import bf.rnc.services.trust.credit.dto.PaymentRequest;
import bf.rnc.services.trust.credit.entity.*;
import bf.rnc.services.trust.credit.repository.CreditRepository;
import bf.rnc.services.trust.credit.service.CreditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests d'intégration — Trust Credit avec PostgreSQL réel (Testcontainers).
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Trust Credit — Tests d'intégration")
class TrustCreditIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("trust_credit_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired private CreditService creditService;
    @Autowired private CreditRepository creditRepository;

    @BeforeEach
    void setUp() {
        creditRepository.deleteAll();
    }

    @Test
    @DisplayName("Cycle complet : demande → approbation → déblocage → paiement → completion")
    void testFullCreditLifecycle() {
        // 1. Demande
        Credit credit = creditService.requestCredit(CreditRequest.builder()
                .citizenId("citizen-int-001")
                .amountXof(30_000L)
                .durationDays(30)
                .purpose(CreditPurpose.MERCHANT)
                .merchantId("merchant-001")
                .build());

        assertThat(credit.getStatus()).isEqualTo(CreditStatus.REQUESTED);

        // 2. Approbation
        credit = creditService.approve(credit.getId(), CreditDecisionRequest.builder()
                .approved(true)
                .escrowAccountId("ESC-001")
                .trustScore(700)
                .build(), "agent-001");

        assertThat(credit.getStatus()).isEqualTo(CreditStatus.APPROVED);
        assertThat(credit.getMaturityDate()).isNotNull();

        // Vérifier échéancier
        var installments = creditService.getInstallments(credit.getId());
        assertThat(installments).hasSize(1); // 30 jours / 30 = 1 échéance
        assertThat(installments.get(0).getTotalXof()).isEqualTo(30_000L);

        // 3. Déblocage
        credit = creditService.disburse(credit.getId());
        assertThat(credit.getStatus()).isEqualTo(CreditStatus.ACTIVE);

        // 4. Paiement complet
        creditService.recordPayment(PaymentRequest.builder()
                .creditId(credit.getId())
                .amountXof(30_000L)
                .paymentChannel(PaymentChannel.MOBILE_MONEY)
                .paymentProvider("ORANGE_MONEY")
                .providerTransactionId("OM-001")
                .idempotencyKey("idem-001")
                .build());

        // 5. Vérifier completion
        var detail = creditService.getCreditDetail(credit.getId());
        assertThat(detail.getStatus()).isEqualTo(CreditStatus.COMPLETED);
        assertThat(detail.getPaidInstallments()).isEqualTo(1);
        assertThat(detail.getOutstandingXof()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Idempotence — paiement dupliqué ignoré")
    void testPaymentIdempotency() {
        Credit credit = createAndApproveCredit("citizen-int-002", 50_000L, 60);
        creditService.disburse(credit.getId());

        PaymentRequest req = PaymentRequest.builder()
                .creditId(credit.getId())
                .amountXof(25_000L)
                .paymentChannel(PaymentChannel.MOBILE_MONEY)
                .idempotencyKey("idem-dup-001")
                .build();

        var p1 = creditService.recordPayment(req);
        var p2 = creditService.recordPayment(req);

        assertThat(p1.getId()).isEqualTo(p2.getId());
    }

    @Test
    @DisplayName("Plafond encours — refus au-delà de 500 000 XOF")
    void testMaxOutstanding() {
        // Créer un crédit de 450k
        Credit credit1 = creditService.requestCredit(CreditRequest.builder()
                .citizenId("citizen-int-003")
                .amountXof(450_000L)
                .durationDays(60)
                .purpose(CreditPurpose.FARMING)
                .build());
        creditService.approve(credit1.getId(), CreditDecisionRequest.builder()
                .approved(true).escrowAccountId("ESC-002").trustScore(750).build(), "agent");
        creditService.disburse(credit1.getId());

        // Tenter un 2e crédit de 60k — doit échouer (450k + 60k > 500k)
        assertThatThrownBy(() -> creditService.requestCredit(CreditRequest.builder()
                .citizenId("citizen-int-003")
                .amountXof(60_000L)
                .durationDays(30)
                .purpose(CreditPurpose.MERCHANT)
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("500 000");
    }

    @Test
    @DisplayName("Annulation crédit approuvé")
    void testCancelApprovedCredit() {
        Credit credit = createAndApproveCredit("citizen-int-004", 20_000L, 30);

        Credit cancelled = creditService.cancel(credit.getId(), "Plus besoin", "citizen-int-004");
        assertThat(cancelled.getStatus()).isEqualTo(CreditStatus.CANCELLED);
    }

    @Test
    @DisplayName("Historique d'événements complet")
    void testEventHistory() {
        Credit credit = createAndApproveCredit("citizen-int-005", 30_000L, 60);

        var history = creditService.getCreditHistory(credit.getId());
        // Au moins 2 événements: CREDIT_REQUESTED + CREDIT_APPROVED
        assertThat(history.size()).isGreaterThanOrEqualTo(2);
        assertThat(history.get(0).getEventType()).isEqualTo("CREDIT_APPROVED");
    }

    @Test
    @DisplayName("Échéancier généré correctement — crédit 90 jours")
    void testInstallmentScheduleGeneration() {
        Credit credit = createAndApproveCredit("citizen-int-006", 90_000L, 90);

        var installments = creditService.getInstallments(credit.getId());
        // 90 / 30 = 3 échéances
        assertThat(installments).hasSize(3);

        // Vérifier que la somme des échéances = montant crédit
        long sum = installments.stream().mapToLong(InstallmentResponse::getPrincipalXof).sum();
        assertThat(sum).isEqualTo(90_000L);

        // Vérifier ordre chronologique
        assertThat(installments.get(0).getInstallmentNumber()).isEqualTo(1);
        assertThat(installments.get(2).getInstallmentNumber()).isEqualTo(3);
        assertThat(installments.get(0).getDueDate())
                .isBefore(installments.get(1).getDueDate());
    }

    // ============================================================
    // Helpers
    // ============================================================

    private Credit createAndApproveCredit(String citizenId, long amount, int durationDays) {
        Credit credit = creditService.requestCredit(CreditRequest.builder()
                .citizenId(citizenId)
                .amountXof(amount)
                .durationDays(durationDays)
                .purpose(CreditPurpose.MERCHANT)
                .build());

        return creditService.approve(credit.getId(), CreditDecisionRequest.builder()
                .approved(true)
                .escrowAccountId("ESC-" + UUID.randomUUID().toString().substring(0, 8))
                .trustScore(700)
                .build(), "agent-001");
    }
}
