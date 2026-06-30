package bf.rnc.common.security.audit;

import bf.rnc.common.lib.audit.AuditEvent;
import bf.rnc.common.security.SecurityContextHelper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.UUID;

/**
 * Aspect AOP qui audite toutes les méthodes annotées {@link Auditable}.
 * Les événements d'audit sont publiés dans Kafka topic "rnc.audit.events" (append-only log).
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final SecurityContextHelper securityContextHelper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        String eventId = UUID.randomUUID().toString();
        String actorId = securityContextHelper.getCurrentUserId();
        String ipAddress = extractClientIp();
        String userAgent = extractUserAgent();

        AuditEvent.AuditEventBuilder eventBuilder = AuditEvent.builder()
                .eventId(eventId)
                .timestamp(Instant.now())
                .actorType(auditable.actorType())
                .actorId(actorId)
                .action(auditable.action())
                .resourceType(auditable.resourceType())
                .ipAddress(ipAddress)
                .userAgent(userAgent);

        try {
            Object result = pjp.proceed();
            eventBuilder.result("SUCCESS");
            if (result != null && auditable.extractResourceId()) {
                eventBuilder.resourceId(result.toString());
            }
            kafkaTemplate.send("rnc.audit.events", eventId, eventBuilder.build());
            return result;
        } catch (Throwable ex) {
            eventBuilder.result("FAILURE");
            kafkaTemplate.send("rnc.audit.events", eventId, eventBuilder.build());
            throw ex;
        }
    }

    private String extractClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String xff = req.getHeader("X-Forwarded-For");
                return xff != null ? xff.split(",")[0].trim() : req.getRemoteAddr();
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    private String extractUserAgent() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return attrs.getRequest().getHeader("User-Agent");
            }
        } catch (Exception ignored) {}
        return "unknown";
    }
}
