package bf.rnc.services.trust.credit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Décision d'approbation d'un crédit (par agent ou système automatique).
 */
@Getter
@Setter
@Builder
public class CreditDecisionRequest {

    /** true = approuver, false = rejeter */
    @NotNull
    private Boolean approved;

    /** Compte escrow à utiliser (obligatoire si approved=true) */
    private String escrowAccountId;

    /** Trust score au moment de la décision (rempli par le système) */
    private Integer trustScore;

    /** Raison du rejet (obligatoire si approved=false) */
    private String rejectionReason;

    /** Notes internes de l'agent */
    private String agentNotes;
}
