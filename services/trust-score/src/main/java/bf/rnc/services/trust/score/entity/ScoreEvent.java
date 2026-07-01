package bf.rnc.services.trust.score.entity;

import bf.rnc.common.lib.audit.BaseAuditFields;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement ayant impacté le score d'un citoyen.
 *
 * <p>Chaque événement est immuable et trace l'impact appliqué au score.
 * Cette table est la source de vérité pour l'explicabilité (conformité réglementaire).</p>
 */
@Entity
@Table(name = "score_events", schema = "trust_score")
@Getter
@Setter
public class ScoreEvent extends BaseAuditFields {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "citizen_id", nullable = false, length = 100)
    private String citizenId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private ScoreEventType eventType;

    /** Référence externe (credit_id, debt_id, etc.) */
    @Column(name = "event_reference", length = 100)
    private String eventReference;

    @Column(name = "impact", nullable = false)
    private Integer impact;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
}
