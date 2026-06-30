package bf.rnc.services.trust.credit.service;

import bf.rnc.common.lib.enums.TrustEnums;
import bf.rnc.common.lib.exception.BusinessException;
import bf.rnc.common.lib.exception.NotFoundException;
import bf.rnc.common.lib.util.IdGenerator;
import bf.rnc.common.lib.util.Money;
import bf.rnc.common.security.audit.Auditable;
import bf.rnc.services.trust.credit.entity.Credit;
import bf.rnc.services.trust.credit.repository.CreditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Service métier — gestion du cycle de vie des nano-crédits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditService {

    private final CreditRepository creditRepository;

    @Auditable(action = "REQUEST_CREDIT", resourceType = "Credit", actorType = "CITIZEN")
    @Transactional
    public Credit requestCredit(String citizenId, Money amount, String purpose,
                                 Integer durationDays, Integer interestRateBps) {
        // Vérifications business
        long activeCredits = creditRepository.countActiveCreditsForCitizen(citizenId);
        if (activeCredits >= 3) {
            throw new BusinessException("MAX_ACTIVE_CREDITS",
                "Un citoyen ne peut pas avoir plus de 3 crédits actifs simultanément");
        }

        long outstanding = creditRepository.totalOutstandingForCitizen(citizenId);
        long maxOutstanding = 500_000L; // 500 000 XOF
        if (outstanding + amount.amount().longValue() > maxOutstanding) {
            throw new BusinessException("MAX_OUTSTANDING_EXCEEDED",
                "Plafond d'encours total dépassé (max 500 000 XOF)");
        }

        Credit credit = new Credit();
        credit.setCreditReference(IdGenerator.prefixed("CR", 12));
        credit.setCitizenId(citizenId);
        credit.setAmount(amount);
        credit.setInterestRateBps(interestRateBps);
        credit.setDurationDays(durationDays);
        credit.setPurpose(purpose);
        credit.setStatus(TrustEnums.CreditStatus.REQUESTED);
        credit.setRequestedAt(Instant.now());

        return creditRepository.save(credit);
    }

    @Transactional
    public Credit approve(UUID creditId, String escrowAccountId, Integer trustScore) {
        Credit credit = creditRepository.findById(creditId)
            .orElseThrow(() -> new NotFoundException("Credit", creditId.toString()));

        if (credit.getStatus() != TrustEnums.CreditStatus.REQUESTED &&
            credit.getStatus() != TrustEnums.CreditStatus.ANALYZED) {
            throw new BusinessException("INVALID_STATUS",
                "Le crédit doit être en statut REQUESTED ou ANALYZED");
        }

        credit.setStatus(TrustEnums.CreditStatus.APPROVED);
        credit.setApprovedAt(Instant.now());
        credit.setEscrowAccountId(escrowAccountId);
        credit.setTrustScoreAtRequest(trustScore);
        credit.setMaturityDate(LocalDate.now().plusDays(credit.getDurationDays()));

        log.info("Credit {} approved for citizen {} — amount={} XOF, maturity={}",
            credit.getCreditReference(), credit.getCitizenId(),
            credit.getAmount(), credit.getMaturityDate());

        return creditRepository.save(credit);
    }

    @Transactional
    public Credit disburse(UUID creditId) {
        Credit credit = creditRepository.findById(creditId)
            .orElseThrow(() -> new NotFoundException("Credit", creditId.toString()));

        if (credit.getStatus() != TrustEnums.CreditStatus.APPROVED) {
            throw new BusinessException("INVALID_STATUS",
                "Le crédit doit être APPROVED pour être débloqué");
        }

        credit.setStatus(TrustEnums.CreditStatus.DISBURSED);
        credit.setDisbursedAt(Instant.now());
        credit.setStatus(TrustEnums.CreditStatus.ACTIVE);

        return creditRepository.save(credit);
    }

    @Transactional
    public Credit reject(UUID creditId, String reason) {
        Credit credit = creditRepository.findById(creditId)
            .orElseThrow(() -> new NotFoundException("Credit", creditId.toString()));

        credit.setStatus(TrustEnums.CreditStatus.REJECTED);
        credit.setRejectionReason(reason);

        return creditRepository.save(credit);
    }

    @Transactional(readOnly = true)
    public Optional<Credit> findById(UUID id) {
        return creditRepository.findById(id);
    }
}
