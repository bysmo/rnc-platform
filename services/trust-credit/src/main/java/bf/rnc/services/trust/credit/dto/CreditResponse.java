package bf.rnc.services.trust.credit.dto;

import bf.rnc.services.trust.credit.entity.CreditPurpose;
import bf.rnc.services.trust.credit.entity.CreditStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Réponse API — Vue publique d'un crédit.
 */
@Getter
@Builder
public class CreditResponse {

    private UUID id;
    private String creditReference;
    private String citizenId;
    private String merchantId;
    private Long amountXof;
    private String currency;
    private Integer interestRateBps;
    private Long totalInterestXof;
    private Long totalRepayableXof;
    private Integer durationDays;
    private CreditPurpose purpose;
    private String escrowAccountId;
    private CreditStatus status;
    private Instant requestedAt;
    private Instant approvedAt;
    private Instant disbursedAt;
    private Instant completedAt;
    private LocalDate maturityDate;
    private Integer trustScoreAtRequest;
    private String rejectionReason;
    private Instant createdAt;
    private Instant updatedAt;

    /** Montant déjà remboursé (calculé depuis les paiements) */
    private Long totalPaidXof;

    /** Montant restant à rembourser */
    private Long outstandingXof;

    /** Nombre d'échéances totales */
    private Integer totalInstallments;

    /** Nombre d'échéances payées */
    private Integer paidInstallments;
}
