package bf.rnc.services.trust.score.dto;

import bf.rnc.services.trust.score.entity.AppealStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Décision sur une contestation de score (par un agent).
 */
@Getter
@Setter
@Builder
public class AppealDecisionRequest {

    @NotNull
    private UUID appealId;

    @NotNull
    private Boolean approved;

    /** Nouveau score si approved=true (optionnel : si null, recalcul automatique) */
    private Integer newScore;

    /** Notes de l'agent */
    private String reviewNotes;
}
