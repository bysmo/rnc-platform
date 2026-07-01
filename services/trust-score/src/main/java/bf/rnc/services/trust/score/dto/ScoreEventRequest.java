package bf.rnc.services.trust.score.dto;

import bf.rnc.services.trust.score.entity.ScoreEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

/**
 * Événement de score à enregistrer (publié par d'autres microservices via Kafka).
 */
@Getter
@Setter
@Builder
public class ScoreEventRequest {

    @NotBlank
    private String citizenId;

    @NotNull
    private ScoreEventType eventType;

    /** Référence externe (credit_id, debt_id, etc.) */
    private String eventReference;

    /** Timestamp de l'événement métier (pas du traitement) */
    private Instant eventTimestamp;

    /** Métadonnées additionnelles (JSON) */
    private Map<String, Object> metadata;

    /** Description optionnelle */
    private String description;
}
