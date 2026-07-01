package bf.rnc.services.trust.credit.service;

import bf.rnc.common.lib.exception.BusinessException;
import bf.rnc.common.lib.exception.NotFoundException;
import bf.rnc.common.lib.util.IdGenerator;
import bf.rnc.common.security.audit.Auditable;
import bf.rnc.services.trust.credit.dto.*;
import bf.rnc.services.trust.credit.entity.*;
import bf.rnc.services.trust.credit.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Service métier central — gestion du cycle de vie des nano-crédits.
 *
 * <p>Cycle :</p>
 * <pre>
 * REQUESTED → ANALYZED → APPROVED → DISBURSED → ACTIVE → COMPLETED
 *                ↓          ↓                       ↓
 *            REJECTED   CANCELLED               DEFAULTED
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditService {

    private static final int MAX_ACTIVE_CREDITS = 3;
    private static final long MAX_OUTSTANDING_XOF = 500_000L;
    private static final int INSTALLMENT_INTERVAL_DAYS = 30;

    private final CreditRepository creditRepository;
    private final RepaymentInstallmentRepository installmentRepository;
    private final PaymentRepository paymentRepository;
    private final CreditEventRepository creditEventRepository;

    // ============================================================
    // DEMANDE DE CRÉDIT
    // ============================================================

    @Auditable(action = "REQUEST_CREDIT", resourceType = "Credit", actorType = "CITIZEN", extractResourceId = true)
    @Transactional
    public Credit requestCredit(CreditRequest request) {
        // Validations business
        validateCreditRequest(request);

        // Vérifier nombre max de crédits actifs
        long activeCredits = creditRepository.countActiveCreditsForCitizen(
                request.getCitizenId(),
                List.of(CreditStatus.ACTIVE, CreditStatus.DISBURSED, CreditStatus.APPROVED));
        if (activeCredits >= MAX_ACTIVE_CREDITS) {
            throw new BusinessException("MAX_ACTIVE_CREDITS",
                "Un citoyen ne peut pas avoir plus de " + MAX_ACTIVE_CREDITS + " crédits actifs simultanément");
        }

        // Vérifier plafond d'encours
        long outstanding = creditRepository.totalOutstandingForCitizen(
                request.getCitizenId(),
                List.of(CreditStatus.ACTIVE, CreditStatus.DISBURSED));
        if (outstanding + request.getAmountXof() > MAX_OUTSTANDING_XOF) {
            throw new BusinessException("MAX_OUTSTANDING_EXCEEDED",
                "Plafond d'encours total dépassé (max 500 000 XOF)");
        }

        // Créer le crédit
        Credit credit = new Credit();
        credit.setCreditReference(IdGenerator.prefixed("CR", 12));
        credit.setCitizenId(request.getCitizenId());
        credit.setMerchantId(request.getMerchantId());
        credit.setAmountMinor(request.getAmountXof());
        credit.setInterestRateBps(request.getInterestRateBps() != null ? request.getInterestRateBps() : 0);
        credit.setDurationDays(request.getDurationDays());
        credit.setPurpose(request.getPurpose());
        credit.setMobileMoneyAccount(request.getMobileMoneyAccount());
        credit.setStatus(CreditStatus.REQUESTED);
        credit.setRequestedAt(Instant.now());

        credit = creditRepository.save(credit);

        // Événement d'audit
        saveEvent(credit.getId(), "CREDIT_REQUESTED", "CITIZEN", request.getCitizenId(),
                "Demande de crédit " + credit.getCreditReference() + " pour " + request.getAmountXof() + " XOF");

        log.info("Crédit demandé: reference={}, citizen={}, amount={} XOF, purpose={}",
                credit.getCreditReference(), request.getCitizenId(), request.getAmountXof(), request.getPurpose());

        return credit;
    }

    // ============================================================
    // DÉCISION
    // ============================================================

    @Auditable(action = "APPROVE_CREDIT", resourceType = "Credit", actorType = "ADMIN")
    @Transactional
    public Credit approve(UUID creditId, CreditDecisionRequest decision, String actorId) {
        Credit credit = findCredit(creditId);

        if (!credit.isEditable()) {
            throw new BusinessException("INVALID_STATUS",
                "Le crédit doit être en statut REQUESTED ou ANALYZED pour être approuvé");
        }

        if (decision.getApproved()) {
            if (decision.getEscrowAccountId() == null || decision.getEscrowAccountId().isBlank()) {
                throw new BusinessException("ESCROW_REQUIRED",
                    "Le compte escrow est obligatoire pour approuver un crédit");
            }

            credit.setStatus(CreditStatus.APPROVED);
            credit.setApprovedAt(Instant.now());
            credit.setEscrowAccountId(decision.getEscrowAccountId());
            credit.setTrustScoreAtRequest(decision.getTrustScore());
            credit.setMaturityDate(LocalDate.now().plusDays(credit.getDurationDays()));
            credit.setRejectionReason(null);

            // Générer l'échéancier
            generateRepaymentSchedule(credit);

            saveEvent(credit.getId(), "CREDIT_APPROVED", "ADMIN", actorId,
                    "Crédit approuvé — escrow: " + decision.getEscrowAccountId());

            log.info("Crédit APPROVED: {} par {} — escrow={}, maturity={}",
                    credit.getCreditReference(), actorId, decision.getEscrowAccountId(), credit.getMaturityDate());
        } else {
            if (decision.getRejectionReason() == null || decision.getRejectionReason().isBlank()) {
                throw new BusinessException("REJECTION_REASON_REQUIRED",
                    "La raison du rejet est obligatoire");
            }

            credit.setStatus(CreditStatus.REJECTED);
            credit.setRejectionReason(decision.getRejectionReason());

            saveEvent(credit.getId(), "CREDIT_REJECTED", "ADMIN", actorId,
                    "Crédit rejeté — raison: " + decision.getRejectionReason());

            log.info("Crédit REJECTED: {} par {} — raison: {}",
                    credit.getCreditReference(), actorId, decision.getRejectionReason());
        }

        return creditRepository.save(credit);
    }

    // ============================================================
    // DÉBLOCAGE
    // ============================================================

    @Auditable(action = "DISBURSE_CREDIT", resourceType = "Credit", actorType = "SYSTEM")
    @Transactional
    public Credit disburse(UUID creditId) {
        Credit credit = findCredit(creditId);

        if (credit.getStatus() != CreditStatus.APPROVED) {
            throw new BusinessException("INVALID_STATUS",
                "Le crédit doit être APPROVED pour être débloqué");
        }

        credit.setStatus(CreditStatus.DISBURSED);
        credit.setDisbursedAt(Instant.now());
        credit = creditRepository.save(credit);

        // Passer immédiatement en ACTIVE (le déblocage effectif est géré par Trust Escrow)
        credit.setStatus(CreditStatus.ACTIVE);
        credit = creditRepository.save(credit);

        saveEvent(credit.getId(), "CREDIT_DISBURSED", "SYSTEM", null,
                "Crédit débloqué — fonds transférés à l'escrow " + credit.getEscrowAccountId());

        log.info("Crédit DISBURSED+ACTIVE: {} — escrow={}",
                credit.getCreditReference(), credit.getEscrowAccountId());

        return credit;
    }

    // ============================================================
    // PAIEMENT
    // ============================================================

    @Auditable(action = "RECORD_PAYMENT", resourceType = "Payment", actorType = "SYSTEM", extractResourceId = true)
    @Transactional
    public Payment recordPayment(PaymentRequest request) {
        // Idempotence
        var existing = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Paiement déjà enregistré — idempotence: {}", request.getIdempotencyKey());
            return existing.get();
        }

        Credit credit = findCredit(request.getCreditId());

        if (!credit.isActive()) {
            throw new BusinessException("CREDIT_NOT_ACTIVE",
                "Le crédit n'est pas actif — impossible d'enregistrer un paiement");
        }

        // Créer le paiement
        Payment payment = new Payment();
        payment.setPaymentReference(IdGenerator.prefixed("PAY", 12));
        payment.setCreditId(credit.getId());
        payment.setInstallmentId(request.getInstallmentId());
        payment.setAmountMinor(request.getAmountXof());
        payment.setPaymentChannel(request.getPaymentChannel());
        payment.setPaymentProvider(request.getPaymentProvider());
        payment.setProviderTransactionId(request.getProviderTransactionId());
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(Instant.now());
        payment.setProcessedAt(Instant.now());
        payment.setIdempotencyKey(request.getIdempotencyKey());

        payment = paymentRepository.save(payment);

        // Affecter à l'échéance la plus ancienne non payée
        applyPaymentToInstallments(credit, payment);

        // Vérifier si crédit complètement remboursé
        checkCreditCompletion(credit);

        saveEvent(credit.getId(), "PAYMENT_RECEIVED", "SYSTEM", null,
                "Paiement de " + request.getAmountXof() + " XOF reçu — réf: " + payment.getPaymentReference());

        log.info("Paiement enregistré: ref={}, credit={}, amount={} XOF",
                payment.getPaymentReference(), credit.getCreditReference(), request.getAmountXof());

        return payment;
    }

    // ============================================================
    // CONSULTATION
    // ============================================================

    @Transactional(readOnly = true)
    public Credit findCreditEntity(UUID creditId) {
        return findCredit(creditId);
    }

    @Transactional(readOnly = true)
    public Credit findCreditByReference(String reference) {
        return creditRepository.findByCreditReferenceAndDeletedFalse(reference)
                .orElseThrow(() -> new NotFoundException("Credit", reference));
    }

    @Transactional(readOnly = true)
    public CreditResponse getCreditDetail(UUID creditId) {
        Credit credit = findCredit(creditId);
        return toResponse(credit);
    }

    @Transactional(readOnly = true)
    public List<InstallmentResponse> getInstallments(UUID creditId) {
        findCredit(creditId); // verify exists
        return installmentRepository.findByCreditIdAndDeletedFalseOrderByInstallmentNumberAsc(creditId)
                .stream()
                .map(this::toInstallmentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CreditEvent> getCreditHistory(UUID creditId) {
        return creditEventRepository.findByCreditIdOrderByEventTimestampDesc(creditId);
    }

    // ============================================================
    // ADMIN
    // ============================================================

    @Auditable(action = "CANCEL_CREDIT", resourceType = "Credit", actorType = "ADMIN")
    @Transactional
    public Credit cancel(UUID creditId, String reason, String actorId) {
        Credit credit = findCredit(creditId);

        if (!credit.isCancellable()) {
            throw new BusinessException("CANNOT_CANCEL",
                "Le crédit ne peut plus être annulé dans son statut actuel: " + credit.getStatus());
        }

        credit.setStatus(CreditStatus.CANCELLED);
        credit = creditRepository.save(credit);

        saveEvent(credit.getId(), "CREDIT_CANCELLED", "ADMIN", actorId, "Annulation: " + reason);

        log.info("Crédit CANCELLED: {} par {} — raison: {}", credit.getCreditReference(), actorId, reason);
        return credit;
    }

    /**
     * Marque les crédits en retard > 30 jours comme DEFAULTED.
     * Appelé par un job planifié quotidien.
     */
    @Auditable(action = "MARK_DEFAULTED", resourceType = "Credit", actorType = "SYSTEM")
    @Transactional
    public int markDefaultedCredits() {
        LocalDate threshold = LocalDate.now().minusDays(30);
        List<Credit> activeCredits = creditRepository.findCreditsMaturedBefore(LocalDate.now());

        int count = 0;
        for (Credit credit : activeCredits) {
            if (credit.getMaturityDate() != null && credit.getMaturityDate().isBefore(threshold)) {
                credit.setStatus(CreditStatus.DEFAULTED);
                creditRepository.save(credit);
                saveEvent(credit.getId(), "CREDIT_DEFAULTED", "SYSTEM", null,
                        "Défaut — maturité dépassée de plus de 30 jours");
                count++;
            }
        }

        if (count > 0) {
            log.warn("{} crédit(s) marqué(s) en DEFAULTED", count);
        }
        return count;
    }

    // ============================================================
    // HELPERS PRIVÉS
    // ============================================================

    private void validateCreditRequest(CreditRequest request) {
        if (request.getAmountXof() < 5000) {
            throw new BusinessException("AMOUNT_TOO_LOW",
                "Le montant minimum est 5 000 XOF");
        }
        if (request.getDurationDays() < 7) {
            throw new BusinessException("DURATION_TOO_SHORT",
                "La durée minimum est 7 jours");
        }
    }

    private Credit findCredit(UUID creditId) {
        return creditRepository.findById(creditId)
                .orElseThrow(() -> new NotFoundException("Credit", creditId.toString()));
    }

    private void generateRepaymentSchedule(Credit credit) {
        int numberOfInstallments = Math.max(1, credit.getDurationDays() / INSTALLMENT_INTERVAL_DAYS);
        if (credit.getDurationDays() % INSTALLMENT_INTERVAL_DAYS != 0) {
            numberOfInstallments++;
        }

        long principalPerInstallment = credit.getAmountMinor() / numberOfInstallments;
        long totalInterest = credit.getTotalInterestMinor();
        long interestPerInstallment = totalInterest / numberOfInstallments;

        // Ajuster le dernier pour le reste
        long principalDistributed = 0;
        long interestDistributed = 0;

        for (int i = 1; i <= numberOfInstallments; i++) {
            RepaymentInstallment inst = new RepaymentInstallment();
            inst.setCreditId(credit.getId());
            inst.setInstallmentNumber(i);
            inst.setDueDate(LocalDate.now().plusDays((long) i * INSTALLMENT_INTERVAL_DAYS));

            long principal = principalPerInstallment;
            long interest = interestPerInstallment;

            if (i == numberOfInstallments) {
                // Dernier : ajuster pour équilibrer
                principal = credit.getAmountMinor() - principalDistributed;
                interest = totalInterest - interestDistributed;
            }

            inst.setPrincipalMinor(principal);
            inst.setInterestMinor(interest);
            inst.setTotalMinor(principal + interest);
            inst.setStatus(InstallmentStatus.PENDING);
            inst.setGracePeriodDays(3); // 3 jours de grâce

            installmentRepository.save(inst);

            principalDistributed += principal;
            interestDistributed += interest;
        }

        log.info("Échéancier généré: {} échéances pour crédit {}",
                numberOfInstallments, credit.getCreditReference());
    }

    private void applyPaymentToInstallments(Credit credit, Payment payment) {
        long remainingAmount = payment.getAmountMinor();

        List<RepaymentInstallment> installments = installmentRepository
                .findByCreditIdAndDeletedFalseOrderByInstallmentNumberAsc(credit.getId());

        for (RepaymentInstallment inst : installments) {
            if (remainingAmount <= 0) break;
            if (inst.getStatus() == InstallmentStatus.PAID
                    || inst.getStatus() == InstallmentStatus.CANCELLED) continue;

            long outstanding = inst.getOutstandingMinor();
            long applied = Math.min(remainingAmount, outstanding);

            inst.setPaidMinor(inst.getPaidMinor() + applied);
            inst.setStatus(inst.isFullyPaid() ? InstallmentStatus.PAID : InstallmentStatus.PARTIALLY_PAID);
            if (inst.isFullyPaid()) {
                inst.setPaidAt(Instant.now());
                inst.setPaymentReference(payment.getPaymentReference());
            }
            installmentRepository.save(inst);

            remainingAmount -= applied;
        }

        // Si reste > 0 (surpaiement), on log mais on ne rembourse pas (à gérer manuellement)
        if (remainingAmount > 0) {
            log.warn("Surpaiement de {} XOF sur crédit {} — à traiter manuellement",
                    remainingAmount, credit.getCreditReference());
        }
    }

    private void checkCreditCompletion(Credit credit) {
        int total = installmentRepository.countTotalInstallments(credit.getId());
        int paid = installmentRepository.countPaidInstallments(credit.getId());

        if (total > 0 && paid == total) {
            credit.setStatus(CreditStatus.COMPLETED);
            credit.setCompletedAt(Instant.now());
            creditRepository.save(credit);
            saveEvent(credit.getId(), "CREDIT_COMPLETED", "SYSTEM", null,
                    "Crédit entièrement remboursé (" + total + "/" + total + " échéances)");
            log.info("Crédit COMPLETED: {} — {} échéances payées", credit.getCreditReference(), total);
        }
    }

    private void saveEvent(UUID creditId, String eventType, String actorType, String actorId, String description) {
        CreditEvent event = new CreditEvent();
        event.setCreditId(creditId);
        event.setEventType(eventType);
        event.setActorType(actorType);
        event.setActorId(actorId);
        event.setDescription(description);
        creditEventRepository.save(event);
    }

    private CreditResponse toResponse(Credit credit) {
        long totalPaid = installmentRepository.sumPaidForCredit(credit.getId());
        int totalInst = installmentRepository.countTotalInstallments(credit.getId());
        int paidInst = installmentRepository.countPaidInstallments(credit.getId());

        return CreditResponse.builder()
                .id(credit.getId())
                .creditReference(credit.getCreditReference())
                .citizenId(credit.getCitizenId())
                .merchantId(credit.getMerchantId())
                .amountXof(credit.getAmountXof())
                .currency(credit.getCurrency())
                .interestRateBps(credit.getInterestRateBps())
                .totalInterestXof(credit.getTotalInterestMinor())
                .totalRepayableXof(credit.getTotalRepayableMinor())
                .durationDays(credit.getDurationDays())
                .purpose(credit.getPurpose())
                .escrowAccountId(credit.getEscrowAccountId())
                .status(credit.getStatus())
                .requestedAt(credit.getRequestedAt())
                .approvedAt(credit.getApprovedAt())
                .disbursedAt(credit.getDisbursedAt())
                .completedAt(credit.getCompletedAt())
                .maturityDate(credit.getMaturityDate())
                .trustScoreAtRequest(credit.getTrustScoreAtRequest())
                .rejectionReason(credit.getRejectionReason())
                .createdAt(credit.getCreatedAt())
                .updatedAt(credit.getUpdatedAt())
                .totalPaidXof(totalPaid)
                .outstandingXof(credit.getTotalRepayableMinor() - totalPaid)
                .totalInstallments(totalInst)
                .paidInstallments(paidInst)
                .build();
    }

    private InstallmentResponse toInstallmentResponse(RepaymentInstallment inst) {
        return InstallmentResponse.builder()
                .id(inst.getId())
                .installmentNumber(inst.getInstallmentNumber())
                .dueDate(inst.getDueDate())
                .principalXof(inst.getPrincipalMinor())
                .interestXof(inst.getInterestMinor())
                .totalXof(inst.getTotalMinor())
                .paidXof(inst.getPaidMinor())
                .outstandingXof(inst.getOutstandingMinor())
                .status(inst.getStatus())
                .gracePeriodDays(inst.getGracePeriodDays())
                .isOverdue(inst.isOverdue(LocalDate.now()))
                .build();
    }
}
