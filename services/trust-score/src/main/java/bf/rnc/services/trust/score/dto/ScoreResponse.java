package bf.rnc.services.trust.score.dto;

import bf.rnc.services.trust.score.entity.ScoreLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Réponse API — Score d'un citoyen avec détail des facteurs.
 */
@Getter
@Builder
public class ScoreResponse {

    private UUID id;
    private String citizenId;
    private Integer scoreValue;
    private ScoreLevel scoreLevel;
    private Instant computedAt;
    private Integer version;

    /** Détail des facteurs pour explicabilité */
    private List<FactorDetail> factors;

    /** Indique si un crédit automatique est possible */
    private boolean automaticCreditAllowed;

    /** Montant maximum recommandé (en XOF) */
    private Long recommendedMaxAmount;

    @Getter
    @Builder
    public static class FactorDetail {
        private String key;
        private String label;
        private Integer weight;
        private Integer subScore;
        private String description;
    }
}
