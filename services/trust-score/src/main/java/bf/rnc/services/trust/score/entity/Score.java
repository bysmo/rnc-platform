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
 * Score courant d'un citoyen.
 *
 * <p>Une seule ligne active par citoyen (via index unique partiel).
 * Le champ {@code factors} contient le détail des facteurs pour l'explicabilité.</p>
 */
@Entity
@Table(name = "scores", schema = "trust_score")
@Getter
@Setter
public class Score extends BaseAuditFields {

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

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt = Instant.now();

    /**
     * Détail JSON des facteurs pour explicabilité.
     * Exemple : {"CREDIT_REPAYMENT": {"weight": 40, "sub_score": 320, "details": "..."}}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "factors", columnDefinition = "jsonb", nullable = false)
    private String factors;

    @Column(name = "version", nullable = false)
    private Integer scoreVersion = 1;

    /**
     * Recalcule le niveau à partir de la valeur.
     */
    public void recalculateLevel() {
        this.scoreLevel = ScoreLevel.fromScore(this.scoreValue);
    }
}
