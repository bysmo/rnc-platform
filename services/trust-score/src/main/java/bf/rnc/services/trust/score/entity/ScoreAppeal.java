package bf.rnc.services.trust.score.entity;

import bf.rnc.common.lib.audit.BaseAuditFields;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Contestation de score déposée par un citoyen.
 *
 * <p>Conformément au principe de transparence algorithmique, tout citoyen
 * peut contester son score et obtenir une révision par un agent humain.</p>
 */
@Entity
@Table(name = "score_appeals", schema = "trust_score")
@Getter
@Setter
public class ScoreAppeal extends BaseAuditFields {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "citizen_id", nullable = false, length = 100)
    private String citizenId;

    @Column(name = "score_at_appeal", nullable = false)
    private Integer scoreAtAppeal;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppealStatus status = AppealStatus.PENDING;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "review_notes", length = 1000)
    private String reviewNotes;

    @Column(name = "score_after_review")
    private Integer scoreAfterReview;
}
