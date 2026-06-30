package bf.rnc.services.trust.id.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Historique immuable des consentements (Loi 010-2004/AN — preuve de consentement).
 */
@Entity
@Table(name = "consent_history", schema = "trust_id")
@Getter
@Setter
public class ConsentHistory {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "citizen_id", nullable = false)
    private UUID citizenId;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 30)
    private ConsentType consentType;

    @Column(name = "granted", nullable = false)
    private Boolean granted;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt = Instant.now();

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "source", nullable = false, length = 30)
    private String source = "MOBILE_APP";
}
