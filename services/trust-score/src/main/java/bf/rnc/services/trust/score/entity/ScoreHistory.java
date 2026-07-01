package bf.rnc.services.trust.score.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Historique immuable des scores d'un citoyen — pour audit réglementaire.
 *
 * <p>Contrairement à {@link Score} qui est mis à jour en place, cette table
 * est append-only et trace tous les changements de score.</p>
 */
@Entity
@Table(name = "score_history", schema = "trust_score")
@Getter
@Setter
public class ScoreHistory {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "citizen_id", nullable = false, length = 100)
    private String citizenId;

    @Column(name = "score_value", nullable = false)
    private Integer scoreValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "score_level", nullable = false, length = 20)
    private ScoreLevel scoreLevel;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 50)
    private ScoreHistorySource source = ScoreHistorySource.COMPUTED;

    @Column(name = "notes", length = 500)
    private String notes;
}
