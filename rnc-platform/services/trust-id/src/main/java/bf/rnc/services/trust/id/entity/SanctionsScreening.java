package bf.rnc.services.trust.id.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Screening de sanctions (LCB-FT) — audit immuable.
 */
@Entity
@Table(name = "sanctions_screenings", schema = "trust_id")
@Getter
@Setter
public class SanctionsScreening {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "citizen_id", nullable = false)
    private UUID citizenId;

    @Column(name = "screening_type", nullable = false, length = 30)
    private String screeningType;

    @Column(name = "screen_date", nullable = false)
    private Instant screenDate = Instant.now();

    @Column(name = "matched", nullable = false)
    private Boolean matched = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matched_entries", columnDefinition = "jsonb")
    private String matchedEntries;

    @Column(name = "score")
    private Integer score;

    @Column(name = "provider", length = 50)
    private String provider;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "jsonb")
    private String rawResponse;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
