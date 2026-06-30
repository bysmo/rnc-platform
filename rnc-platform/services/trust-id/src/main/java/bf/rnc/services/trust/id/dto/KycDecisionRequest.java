package bf.rnc.services.trust.id.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Décision de validation KYC par un agent.
 */
@Getter
@Setter
@Builder
public class KycDecisionRequest {

    @NotNull(message = "L'ID du citoyen est obligatoire")
    private UUID citizenId;

    /** true = APPROVED, false = REJECTED */
    @NotNull
    private Boolean approved;

    /** Raison du rejet (obligatoire si approved=false) */
    private String rejectionReason;
}
