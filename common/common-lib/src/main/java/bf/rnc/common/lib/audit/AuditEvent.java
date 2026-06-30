package bf.rnc.common.lib.audit;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * Événement d'audit — journalisé de façon immuable (append-only).
 * Conformément aux exigences BCEAO et Loi 010-2004/AN.
 */
@Getter
@Builder
public class AuditEvent {
    private String eventId;
    private Instant timestamp;
    private String actorType;      // CITIZEN, MERCHANT, ADMIN, SYSTEM
    private String actorId;
    private String action;         // CREATE_CREDIT, SCAN_QR, SIGN_DEBT...
    private String resourceType;
    private String resourceId;
    private String ipAddress;
    private String userAgent;
    private Map<String, Object> details;
    private String result;         // SUCCESS, FAILURE
}
