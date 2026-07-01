package bf.rnc.services.trust.credit.dto;

import bf.rnc.services.trust.credit.entity.InstallmentStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Vue d'une échéance de remboursement.
 */
@Getter
@Builder
public class InstallmentResponse {

    private UUID id;
    private Integer installmentNumber;
    private LocalDate dueDate;
    private Long principalXof;
    private Long interestXof;
    private Long totalXof;
    private Long paidXof;
    private Long outstandingXof;
    private InstallmentStatus status;
    private Integer gracePeriodDays;
    private Boolean isOverdue;
}
