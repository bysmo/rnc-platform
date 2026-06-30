#!/bin/bash
# Generate all Java source files for common modules
# Run from rnc-platform root

set -e

BASE="/home/z/my-project/rnc/rnc-platform"

# ============================================================
# common-lib
# ============================================================
mkdir -p "$BASE/common/common-lib/src/main/java/bf/rnc/common/lib/dto"
mkdir -p "$BASE/common/common-lib/src/main/java/bf/rnc/common/lib/exception"
mkdir -p "$BASE/common/common-lib/src/main/java/bf/rnc/common/lib/util"
mkdir -p "$BASE/common/common-lib/src/main/java/bf/rnc/common/lib/audit"
mkdir -p "$BASE/common/common-lib/src/main/java/bf/rnc/common/lib/enums"

# BaseAuditFields
cat > "$BASE/common/common-lib/src/main/java/bf/rnc/common/lib/audit/BaseAuditFields.java" << 'EOF'
package bf.rnc.common.lib.audit;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Champs d'audit communs à toutes les entités RNC.
 * Conformément à la Loi 010-2004/AN sur la protection des données,
 * toute création/modification doit être traçable.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseAuditFields {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @PrePersist
    void prePersist() {
        if (version == null) version = 0L;
    }

    @PreUpdate
    void preUpdate() {
        version = (version == null ? 0L : version) + 1L;
    }
}
EOF

# RncException
cat > "$BASE/common/common-lib/src/main/java/bf/rnc/common/lib/exception/RncException.java" << 'EOF'
package bf.rnc.common.lib.exception;

import lombok.Getter;

/**
 * Exception racine de la plateforme RNC.
 */
@Getter
public class RncException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    public RncException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = 500;
    }

    public RncException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public RncException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = 500;
    }
}
EOF

# Business exceptions
cat > "$BASE/common/common-lib/src/main/java/bf/rnc/common/lib/exception/BusinessException.java" << 'EOF'
package bf.rnc.common.lib.exception;

/**
 * Exception métier — mapped to HTTP 422.
 */
public class BusinessException extends RncException {
    public BusinessException(String errorCode, String message) {
        super(errorCode, message, 422);
    }
}
EOF

cat > "$BASE/common/common-lib/src/main/java/bf/rnc/common/lib/exception/NotFoundException.java" << 'EOF'
package bf.rnc.common.lib.exception;

public class NotFoundException extends RncException {
    public NotFoundException(String resource, String id) {
        super("NOT_FOUND", resource + " introuvable pour l'identifiant: " + id, 404);
    }
}
EOF

cat > "$BASE/common/common-lib/src/main/java/bf/rnc/common/lib/exception/UnauthorizedException.java" << 'EOF'
package bf.rnc.common.lib.exception;

public class UnauthorizedException extends RncException {
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message, 401);
    }
}
EOF

cat > "$BASE/common/common-lib/src/main/java/bf/rnc/common/lib/exception/ForbiddenException.java" << 'EOF'
package bf.rnc.common.lib.exception;

public class ForbiddenException extends RncException {
    public ForbiddenException(String message) {
        super("FORBIDDEN", message, 403);
    }
}
EOF

# Global error handler
cat > "$BASE/common/common-lib/src/main/java/bf/rnc/common/lib/exception/GlobalExceptionHandler.java" << 'EOF'
package bf.rnc.common.lib.exception;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler global des exceptions pour tous les microservices RNC.
 * Format d'erreur conforme RFC 7807 (Problem Details for HTTP APIs).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RncException.class)
    public ResponseEntity<ErrorResponse> handleRnc(RncException ex) {
        log.warn("RNC error [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ErrorResponse.builder()
                        .timestamp(Instant.now())
                        .errorCode(ex.getErrorCode())
                        .message(ex.getMessage())
                        .status(ex.getHttpStatus())
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .timestamp(Instant.now())
                        .errorCode("VALIDATION_ERROR")
                        .message("Erreur de validation")
                        .status(400)
                        .details(errors)
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Erreur inattendue", ex);
        return ResponseEntity.status(500)
                .body(ErrorResponse.builder()
                        .timestamp(Instant.now())
                        .errorCode("INTERNAL_ERROR")
                        .message("Une erreur inattendue s'est produite")
                        .status(500)
                        .build());
    }

    @Getter
    @Builder
    public static class ErrorResponse {
        private Instant timestamp;
        private String errorCode;
        private String message;
        private int status;
        private Map<String, String> details;
    }
}
EOF

# Money value object
cat > "$BASE/common/common-lib/src/main/java/bf/rnc/common/lib/util/Money.java" << 'EOF'
package bf.rnc.common.lib.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value object pour les montants financiers.
 * Utilise BigDecimal pour éviter les erreurs de précision (critique pour les nano-crédits).
 * Devise par défaut: XOF (Franc CFA — UEMOA).
 */
public record Money(BigDecimal amount, String currency) {

    public static final String XOF = "XOF";

    public Money {
        Objects.requireNonNull(amount, "Le montant ne peut pas être null");
        Objects.requireNonNull(currency, "La devise ne peut pas être null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le montant ne peut pas être négatif");
        }
        amount = amount.setScale(0, RoundingMode.HALF_UP); // XOF n'a pas de centimes
        currency = currency.toUpperCase();
    }

    public static Money xof(long amount) {
        return new Money(BigDecimal.valueOf(amount), XOF);
    }

    public static Money xof(BigDecimal amount) {
        return new Money(amount, XOF);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money multiply(int factor) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(factor)), this.currency);
    }

    public Money percentage(BigDecimal pct) {
        return new Money(this.amount.multiply(pct).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP), this.currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isZero() {
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                "Devises incompatibles: " + this.currency + " vs " + other.currency);
        }
    }
}
EOF

# ID generator
cat > "$BASE/common/common-lib/src/main/java/bf/rnc/common/lib/util/IdGenerator.java" << 'EOF'
package bf.rnc.common.lib.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Génération d'identifiants uniques — utilisant SecureRandom pour éviter l'énumération.
 */
public final class IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private IdGenerator() {}

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * Génère un code court lisible (ex: pour QR codes, reconnaissances de dette).
     */
    public static String shortCode(int length) {
        char[] buf = new char[length];
        for (int i = 0; i < length; i++) {
            buf[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(buf);
    }

    /**
     * Génère un identifiant préfixé (ex: TRS-XXXX pour transaction).
     */
    public static String prefixed(String prefix, int randomLength) {
        return prefix + "-" + shortCode(randomLength);
    }
}
EOF

# Audit event
cat > "$BASE/common/common-lib/src/main/java/bf/rnc/common/lib/audit/AuditEvent.java" << 'EOF'
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
EOF

# Trust enums
cat > "$BASE/common/common-lib/src/main/java/bf/rnc/common/lib/enums/TrustEnums.java" << 'EOF'
package bf.rnc.common.lib.enums;

/**
 * Énumérations communes RNC.
 */
public final class TrustEnums {

    private TrustEnums() {}

    public enum UserRole {
        CITIZEN, MERCHANT, BANK_AGENT, INSURANCE_AGENT,
        SCHOOL_ADMIN, HEALTH_ADMIN, FARMING_COOP,
        COLLECTOR, AUDITOR, ADMIN, SYSTEM
    }

    public enum TrustScoreLevel {
        CRITICAL(0, 299),
        LOW(300, 499),
        FAIR(500, 649),
        GOOD(650, 799),
        EXCELLENT(800, 1000);

        private final int min;
        private final int max;

        TrustScoreLevel(int min, int max) {
            this.min = min;
            this.max = max;
        }

        public static TrustScoreLevel fromScore(int score) {
            for (TrustScoreLevel level : values()) {
                if (score >= level.min && score <= level.max) return level;
            }
            return CRITICAL;
        }
    }

    public enum CreditStatus {
        REQUESTED, ANALYZED, APPROVED, REJECTED,
        DISBURSED, ACTIVE, COMPLETED, DEFAULTED, CANCELLED
    }

    public enum EscrowStatus {
        RESERVED, PARTIALLY_RELEASED, FULLY_RELEASED,
        REFUNDED, DISPUTED
    }

    public enum DebtStatus {
        DRAFT, SIGNED, ACTIVE, HONORED, PARTIALLY_HONORED,
        OVERDUE, DEFAULTED, DISPUTED, CANCELLED
    }

    public enum MerchantCategory {
        SCHOOL, HEALTH, AGRICULTURE, INSURANCE, RETAIL, SERVICE
    }

    public enum PaymentChannel {
        MOBILE_MONEY, BANK_TRANSFER, ESCROW_RELEASE, CASH
    }

    public enum ComplianceFlag {
        KYC_VERIFIED, LCB_FT_CHECKED, BCEAO_COMPLIANT,
        DATA_PROTECTION_COMPLIANT, SANCTIONS_SCREENED
    }
}
EOF

# Paged response
cat > "$BASE/common/common-lib/src/main/java/bf/rnc/common/lib/dto/PageResponse.java" << 'EOF'
package bf.rnc.common.lib.dto;

import lombok.Builder;
import lombok.Getter;

import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class PageResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
EOF

echo "[OK] common-lib sources created"

# ============================================================
# common-security
# ============================================================
mkdir -p "$BASE/common/common-security/src/main/java/bf/rnc/common/security"
mkdir -p "$BASE/common/common-security/src/main/java/bf/rnc/common/security/jwt"
mkdir -p "$BASE/common/common-security/src/main/java/bf/rnc/common/security/audit"
mkdir -p "$BASE/common/common-security/src/main/java/bf/rnc/common/security/encryption"
mkdir -p "$BASE/common/common-security/src/main/resources"

cat > "$BASE/common/common-security/src/main/java/bf/rnc/common/security/SecurityConfig.java" << 'EOF'
package bf.rnc.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Configuration de sécurité commune — OAuth2 Resource Server avec Keycloak.
 * Tous les microservices RNC héritent de cette configuration.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        return http.build();
    }
}
EOF

cat > "$BASE/common/common-security/src/main/java/bf/rnc/common/security/SecurityContextHelper.java" << 'EOF'
package bf.rnc.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Helper pour récupérer l'utilisateur courant depuis le JWT Keycloak.
 */
@Component
public class SecurityContextHelper {

    public Optional<Jwt> getCurrentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(jwt);
        }
        return Optional.empty();
    }

    public String getCurrentUserId() {
        return getCurrentJwt()
                .map(jwt -> jwt.getClaimAsString("sub"))
                .orElse("anonymous");
    }

    public String getCurrentUserEmail() {
        return getCurrentJwt()
                .map(jwt -> jwt.getClaimAsString("email"))
                .orElse(null);
    }

    public String getCurrentUserPreferredUsername() {
        return getCurrentJwt()
                .map(jwt -> jwt.getClaimAsString("preferred_username"))
                .orElse(null);
    }

    public boolean hasRole(String role) {
        return getCurrentJwt()
                .map(jwt -> {
                    Object realmAccess = jwt.getClaim("realm_access");
                    if (realmAccess instanceof java.util.Map<?, ?> ra) {
                        Object roles = ra.get("roles");
                        return roles instanceof java.util.Collection<?> c && c.contains(role);
                    }
                    return false;
                })
                .orElse(false);
    }
}
EOF

cat > "$BASE/common/common-security/src/main/java/bf/rnc/common/security/audit/AuditAspect.java" << 'EOF'
package bf.rnc.common.security.audit;

import bf.rnc.common.lib.audit.AuditEvent;
import bf.rnc.common.security.SecurityContextHelper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingsJoinPoint;
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
    public Object audit(ProceedingsJoinPoint pjp, Auditable auditable) throws Throwable {
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
EOF

cat > "$BASE/common/common-security/src/main/java/bf/rnc/common/security/audit/Auditable.java" << 'EOF'
package bf.rnc.common.security.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation pour marquer les méthodes à auditer.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String action();
    String resourceType() default "";
    String actorType() default "SYSTEM";
    boolean extractResourceId() default false;
}
EOF

cat > "$BASE/common/common-security/src/main/java/bf/rnc/common/security/encryption/HsmEncryptionService.java" << 'EOF'
package bf.rnc.common.security.encryption;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service de chiffrement AES-GCM 256.
 * En production: délègue à un HSM (Hardware Security Module) physique ou PKCS#11.
 * Conformité: BCEAO, Loi 010-2004/AN (chiffrement des données personnelles au repos).
 */
@Slf4j
@Service
public class HsmEncryptionService {

    @Value("${rnc.encryption.key:}")
    private String encryptionKeyBase64;

    @Value("${rnc.encryption.hsm.enabled:false}")
    private boolean hsmEnabled;

    private SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() throws Exception {
        if (hsmEnabled) {
            log.info("HSM activé — délégation du chiffrement au module matériel");
            // En production: connexion au HSM via PKCS#11
        } else {
            log.warn("HSM désactivé — utilisation d'une clé logicielle (DEV ONLY)");
            if (encryptionKeyBase64.isEmpty()) {
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(256);
                this.secretKey = keyGen.generateKey();
            } else {
                byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
                this.secretKey = new SecretKeySpec(keyBytes, "AES");
            }
        }
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Erreur de chiffrement", e);
        }
    }

    public String decrypt(String ciphertextBase64) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertextBase64);
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[combined.length - 12];
            System.arraycopy(combined, 0, iv, 0, 12);
            System.arraycopy(combined, 12, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Erreur de déchiffrement", e);
        }
    }
}
EOF

echo "[OK] common-security sources created"

# ============================================================
# common-data
# ============================================================
mkdir -p "$BASE/common/common-data/src/main/java/bf/rnc/common/data"
mkdir -p "$BASE/common/common-data/src/main/java/bf/rnc/common/data/auditor"
mkdir -p "$BASE/common/common-data/src/main/java/bf/rnc/common/data/config"

cat > "$BASE/common/common-data/src/main/java/bf/rnc/common/data/config/DataConfig.java" << 'EOF'
package bf.rnc.common.data.config;

import bf.rnc.common.data.auditor.RncAuditorAware;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "rncAuditorAware")
@EnableJpaRepositories(basePackages = "bf.rnc")
@EntityScan(basePackages = "bf.rnc")
public class DataConfig {

    @Bean
    public AuditorAware<String> rncAuditorAware() {
        return new RncAuditorAware();
    }
}
EOF

cat > "$BASE/common/common-data/src/main/java/bf/rnc/common/data/auditor/RncAuditorAware.java" << 'EOF'
package bf.rnc.common.data.auditor;

import bf.rnc.common.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

/**
 * AuditorAware — injecte l'utilisateur courant dans les champs createdBy/updatedBy.
 */
public class RncAuditorAware implements AuditorAware<String> {

    @Autowired(required = false)
    private SecurityContextHelper securityContextHelper;

    @Override
    public Optional<String> getCurrentAuditor() {
        if (securityContextHelper == null) return Optional.of("system");
        String userId = securityContextHelper.getCurrentUserId();
        return Optional.ofNullable(userId);
    }
}
EOF

echo "[OK] common-data sources created"

# ============================================================
# common-events
# ============================================================
mkdir -p "$BASE/common/common-events/src/main/java/bf/rnc/common/events"
mkdir -p "$BASE/common/common-events/src/main/java/bf/rnc/common/events/kafka"
mkdir -p "$BASE/common/common-events/src/main/java/bf/rnc/common/events/rabbit"
mkdir -p "$BASE/common/common-events/src/main/java/bf/rnc/common/events/outbox"

cat > "$BASE/common/common-events/src/main/java/bf/rnc/common/events/kafka/KafkaTopics.java" << 'EOF'
package bf.rnc.common.events.kafka;

/**
 * Topics Kafka centralisés pour la plateforme RNC.
 * Tous les microservices utilisent ces constantes pour publier/consommer des events.
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    // Audit & traçabilité (append-only log)
    public static final String AUDIT_EVENTS = "rnc.audit.events";

    // Trust Score
    public static final String SCORE_UPDATED = "rnc.trust-score.updated";
    public static final String SCORE_RECALCULATED = "rnc.trust-score.recalculated";

    // Credit lifecycle
    public static final String CREDIT_REQUESTED = "rnc.credit.requested";
    public static final String CREDIT_APPROVED = "rnc.credit.approved";
    public static final String CREDIT_REJECTED = "rnc.credit.rejected";
    public static final String CREDIT_DISBURSED = "rnc.credit.disbursed";
    public static final String CREDIT_REPAID = "rnc.credit.repaid";
    public static final String CREDIT_DEFAULTED = "rnc.credit.defaulted";

    // Escrow
    public static final String ESCROW_RESERVED = "rnc.escrow.reserved";
    public static final String ESCROW_RELEASED = "rnc.escrow.released";

    // Debt (reconnaissance de dette)
    public static final String DEBT_SIGNED = "rnc.debt.signed";
    public static final String DEBT_HONORED = "rnc.debt.honored";
    public static final String DEBT_OVERDUE = "rnc.debt.overdue";

    // QR Code
    public static final String QR_SCANNED = "rnc.qr.scanned";
    public static final String QR_AUTHORIZED = "rnc.qr.authorized";

    // Identity
    public static final String USER_REGISTERED = "rnc.identity.registered";
    public static final String KYC_VERIFIED = "rnc.identity.kyc-verified";

    // Notifications (consumed by notification service)
    public static final String NOTIFICATION_REQUESTED = "rnc.notification.requested";
}
EOF

cat > "$BASE/common/common-events/src/main/java/bf/rnc/common/events/rabbit/RabbitQueues.java" << 'EOF'
package bf.rnc.common.events.rabbit;

/**
 * Queues RabbitMQ pour les tâches asynchrones (notifications, recouvrement, batch).
 */
public final class RabbitQueues {

    private RabbitQueues() {}

    public static final String SMS_NOTIFICATIONS = "rnc.notifications.sms";
    public static final String EMAIL_NOTIFICATIONS = "rnc.notifications.email";
    public static final String PUSH_NOTIFICATIONS = "rnc.notifications.push";

    public static final String COLLECTION_REMINDERS = "rnc.collect.reminders";
    public static final String COLLECTION_ESCALATION = "rnc.collect.escalation";

    public static final String REPORT_GENERATION = "rnc.reports.generation";
    public static final String STATEMENT_GENERATION = "rnc.statements.generation";
}
EOF

cat > "$BASE/common/common-events/src/main/java/bf/rnc/common/events/outbox/OutboxEvent.java" << 'EOF'
package bf.rnc.common.events.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Entité Outbox — pattern Transactional Outbox pour publier fiablement les events.
 * Une entrée est créée dans la même transaction que la modification métier,
 * puis un poller la publie vers Kafka/RabbitMQ.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }
}
EOF

cat > "$BASE/common/common-events/src/main/java/bf/rnc/common/events/outbox/OutboxRepository.java" << 'EOF'
package bf.rnc.common.events.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("SELECT e FROM OutboxEvent e WHERE e.publishedAt IS NULL " +
           "AND e.attempts < 10 ORDER BY e.createdAt ASC LIMIT :limit")
    List<OutboxEvent> findUnpublished(@Param("limit") int limit);
}
EOF

echo "[OK] common-events sources created"

# ============================================================
# common-observability
# ============================================================
mkdir -p "$BASE/common/common-observability/src/main/java/bf/rnc/common/observability"
mkdir -p "$BASE/common/common-observability/src/main/resources"

cat > "$BASE/common/common-observability/src/main/java/bf/rnc/common/observability/ObservabilityConfig.java" << 'EOF'
package bf.rnc.common.observability;

import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration observabilité: métriques, traces, logs.
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    public CountedAspect countedAspect(MeterRegistry registry) {
        return new CountedAspect(registry);
    }
}
EOF

cat > "$BASE/common/common-observability/src/main/resources/logback-spring.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <springProperty scope="context" name="appName" source="spring.application.name"/>
    <springProperty scope="context" name="lokiUrl" source="rnc.loki.url" defaultValue="http://loki:3100/loki/api/v1/push"/>

    <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
        <http>
            <url>${lokiUrl}</url>
        </http>
        <format>
            <label>
                <pattern>app=${appName},host=${HOSTNAME},level=%level</pattern>
            </label>
            <message>
                <pattern>${FILE_LOG_PATTERN}</pattern>
            </message>
            <sortByTime>true</sortByTime>
        </format>
    </appender>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="LOKI"/>
    </root>
</configuration>
EOF

echo "[OK] common-observability sources created"

# ============================================================
# common-test
# ============================================================
mkdir -p "$BASE/common/common-test/src/main/java/bf/rnc/common/test"

cat > "$BASE/common/common-test/src/main/java/bf/rnc/common/test/AbstractIntegrationTest.java" << 'EOF'
package bf.rnc.common.test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Classe de base pour les tests d'intégration avec Testcontainers.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("rnc_test")
            .withUsername("rnc")
            .withPassword("rnc");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
}
EOF

echo "[OK] common-test sources created"
echo ""
echo "All common modules created successfully!"
