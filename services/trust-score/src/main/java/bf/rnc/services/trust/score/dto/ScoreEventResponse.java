package bf.rnc.services.trust.score.dto;

import bf.rnc.services.trust.score.entity.ScoreEvent;
import bf.rnc.services.trust.score.entity.ScoreEventType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Réponse API — Événement de score (pour historique).
 */
@Getter
@Builder
public class ScoreEventResponse {

    private UUID id;
    private String citizenId;
    private ScoreEventType eventType;
    private String eventReference;
    private Integer impact;
    private String description;
    private Instant eventTimestamp;
    private Instant processedAt;

    public static ScoreEventResponse from(ScoreEvent event) {
        return ScoreEventResponse.builder()
                .id(event.getId())
                .citizenId(event.getCitizenId())
                .eventType(event.getEventType())
                .eventReference(event.getEventReference())
                .impact(event.getImpact())
                .description(event.getDescription())
                .eventTimestamp(event.getEventTimestamp())
                .processedAt(event.getProcessedAt())
                .build();
    }
}
