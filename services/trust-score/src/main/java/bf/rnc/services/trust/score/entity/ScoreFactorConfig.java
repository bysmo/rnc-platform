package bf.rnc.services.trust.score.entity;

import bf.rnc.common.lib.audit.BaseAuditFields;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Configuration d'un facteur de scoring.
 *
 * <p>Les facteurs et leurs poids sont configurables et audités.
 * Toute modification est tracée dans l'historique (immutable).</p>
 */
@Entity
@Table(name = "score_factors_config", schema = "trust_score")
@Getter
@Setter
public class ScoreFactorConfig extends BaseAuditFields {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "factor_key", nullable = false, unique = true, length = 50)
    private String factorKey;

    @Column(name = "factor_label", nullable = false, length = 200)
    private String factorLabel;

    @Column(name = "weight", nullable = false)
    private Integer weight;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
