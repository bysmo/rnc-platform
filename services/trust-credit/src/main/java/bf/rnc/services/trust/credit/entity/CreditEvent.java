package bf.rnc.services.trust.credit.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement du cycle de vie d'un crédit (audit immuable).
 */
@Entity
@Table(name = "credit_events", schema = "trust_credit")
@Getter
@Setter
public class CreditEvent {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "credit_id", nullable = false)
    private UUID creditId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp = Instant.now();

    @Column(name = "actor_type", nullable = false, length = 30)
    private String actorType = "SYSTEM";

    @Column(name = "actor_id", length = 100)
    private String actorId;

    @Column(name = "description", length = 500)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
