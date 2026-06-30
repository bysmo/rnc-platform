#!/bin/bash
# Generate infrastructure services: Eureka, Config Server, Gateway, Auth
set -e
BASE="/home/z/my-project/rnc/rnc-platform"

# ============================================================
# Eureka Server
# ============================================================
mkdir -p "$BASE/infrastructure/eureka-server/src/main/java/bf/rnc/infrastructure/eureka"
mkdir -p "$BASE/infrastructure/eureka-server/src/main/resources"

cat > "$BASE/infrastructure/eureka-server/pom.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>bf.rnc</groupId>
        <artifactId>rnc-platform</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>
    <artifactId>eureka-server</artifactId>
    <name>RNC :: Infrastructure :: Eureka Server</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>bf.rnc</groupId>
            <artifactId>common-observability</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
EOF

cat > "$BASE/infrastructure/eureka-server/src/main/java/bf/rnc/infrastructure/eureka/EurekaServerApplication.java" << 'EOF'
package bf.rnc.infrastructure.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
EOF

cat > "$BASE/infrastructure/eureka-server/src/main/resources/application.yml" << 'EOF'
server:
  port: 8761

spring:
  application:
    name: eureka-server

eureka:
  instance:
    hostname: ${EUREKA_HOST:localhost}
    preferIpAddress: true
  client:
    registerWithEureka: ${EUREKA_REGISTER:false}
    fetchRegistry: ${EUREKA_FETCH:false}
    serviceUrl:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}
  server:
    enableSelfPreservation: true
    evictionIntervalMsInTimer: 60000
    responseCacheUpdateIntervalMs: 30000

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized

logging:
  level:
    com.netflix.eureka: WARN
    com.netflix.discovery: WARN
EOF

# ============================================================
# Config Server
# ============================================================
mkdir -p "$BASE/infrastructure/config-server/src/main/java/bf/rnc/infrastructure/config"
mkdir -p "$BASE/infrastructure/config-server/src/main/resources"
mkdir -p "$BASE/infrastructure/config-server/src/main/resources/config"

cat > "$BASE/infrastructure/config-server/pom.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>bf.rnc</groupId>
        <artifactId>rnc-platform</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>
    <artifactId>config-server</artifactId>
    <name>RNC :: Infrastructure :: Config Server</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-config-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>bf.rnc</groupId>
            <artifactId>common-observability</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
EOF

cat > "$BASE/infrastructure/config-server/src/main/java/bf/rnc/infrastructure/config/ConfigServerApplication.java" << 'EOF'
package bf.rnc.infrastructure.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

@SpringBootApplication
@EnableConfigServer
@EnableEurekaClient
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
EOF

cat > "$BASE/infrastructure/config-server/src/main/resources/application.yml" << 'EOF'
server:
  port: 8888

spring:
  application:
    name: config-server
  profiles:
    active: ${CONFIG_PROFILE:native}
  cloud:
    config:
      server:
        native:
          search-locations: classpath:/config
        git:
          uri: ${CONFIG_GIT_URI:https://gitlab.rnc.bf/rnc/config-repo.git}
          username: ${CONFIG_GIT_USER:}
          password: ${CONFIG_GIT_PASS:}
          default-label: main
          search-paths: '{application}'

eureka:
  client:
    serviceUrl:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}
  instance:
    preferIpAddress: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,refresh
  endpoint:
    health:
      show-details: when-authorized

encrypt:
  key: ${CONFIG_ENCRYPT_KEY:rnc-dev-encryption-key-change-in-production}
EOF

# ============================================================
# API Gateway
# ============================================================
mkdir -p "$BASE/infrastructure/api-gateway/src/main/java/bf/rnc/infrastructure/gateway"
mkdir -p "$BASE/infrastructure/api-gateway/src/main/java/bf/rnc/infrastructure/gateway/filter"
mkdir -p "$BASE/infrastructure/api-gateway/src/main/resources"

cat > "$BASE/infrastructure/api-gateway/pom.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>bf.rnc</groupId>
        <artifactId>rnc-platform</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>
    <artifactId>api-gateway</artifactId>
    <name>RNC :: Infrastructure :: API Gateway</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>
        <dependency>
            <groupId>bf.rnc</groupId>
            <artifactId>common-lib</artifactId>
        </dependency>
        <dependency>
            <groupId>bf.rnc</groupId>
            <artifactId>common-observability</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
EOF

cat > "$BASE/infrastructure/api-gateway/src/main/java/bf/rnc/infrastructure/gateway/ApiGatewayApplication.java" << 'EOF'
package bf.rnc.infrastructure.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

@SpringBootApplication
@EnableEurekaClient
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
EOF

cat > "$BASE/infrastructure/api-gateway/src/main/java/bf/rnc/infrastructure/gateway/filter/RequestLoggingFilter.java" << 'EOF'
package bf.rnc.infrastructure.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Filtre global — ajoute un correlation ID et journalise toutes les requêtes entrantes.
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final String CORRELATION_ID = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders()
                .getFirst(CORRELATION_ID);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        final String cid = correlationId;
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(CORRELATION_ID, cid)
                .build();

        long start = System.currentTimeMillis();
        log.info("[{}] → {} {}", cid, request.getMethod(), request.getURI().getPath());

        return chain.filter(exchange.mutate().request(request).build())
                .then(Mono.fromRunnable(() -> {
                    long duration = System.currentTimeMillis() - start;
                    log.info("[{}] ← {} ({} ms)", cid,
                            exchange.getResponse().getStatusCode(), duration);
                }));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
EOF

cat > "$BASE/infrastructure/api-gateway/src/main/resources/application.yml" << 'EOF'
server:
  port: 8080

spring:
  application:
    name: api-gateway
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_URL:http://localhost:8090}/realms/rnc
          jwk-set-uri: ${KEYCLOAK_URL:http://localhost:8090}/realms/rnc/protocol/openid-connect/certs
  cloud:
    gateway:
      discovery:
        locator:
          enabled: false
      default-filters:
        - name: RequestRateLimiter
          args:
            redis-rate-limiter.replenishRate: 100
            redis-rate-limiter.burstCapacity: 200
      routes:
        - id: trust-id
          uri: lb://trust-id
          predicates:
            - Path=/api/v1/identity/**
        - id: trust-score
          uri: lb://trust-score
          predicates:
            - Path=/api/v1/score/**
        - id: trust-qr
          uri: lb://trust-qr
          predicates:
            - Path=/api/v1/qr/**
        - id: trust-credit
          uri: lb://trust-credit
          predicates:
            - Path=/api/v1/credits/**
        - id: trust-escrow
          uri: lb://trust-escrow
          predicates:
            - Path=/api/v1/escrow/**
        - id: trust-school
          uri: lb://trust-school
          predicates:
            - Path=/api/v1/school/**
        - id: trust-health
          uri: lb://trust-health
          predicates:
            - Path=/api/v1/health-financing/**
        - id: trust-farming
          uri: lb://trust-farming
          predicates:
            - Path=/api/v1/farming/**
        - id: trust-insurance
          uri: lb://trust-insurance
          predicates:
            - Path=/api/v1/insurance/**
        - id: trust-debt
          uri: lb://trust-debt
          predicates:
            - Path=/api/v1/debts/**
        - id: trust-collect
          uri: lb://trust-collect
          predicates:
            - Path=/api/v1/collect/**
        - id: trust-merchant
          uri: lb://trust-merchant
          predicates:
            - Path=/api/v1/merchants/**

eureka:
  client:
    serviceUrl:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}
  instance:
    preferIpAddress: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,gateway
  endpoint:
    health:
      show-details: when-authorized

rnc:
  cors:
    allowed-origins: ${CORS_ORIGINS:http://localhost:3000,http://localhost:8081}
  rate-limit:
    enabled: true
EOF

echo "[OK] Infrastructure services created"

# ============================================================
# Auth Service (Keycloak bootstrap + custom endpoints)
# ============================================================
mkdir -p "$BASE/infrastructure/auth-service/src/main/java/bf/rnc/infrastructure/auth"
mkdir -p "$BASE/infrastructure/auth-service/src/main/java/bf/rnc/infrastructure/auth/controller"
mkdir -p "$BASE/infrastructure/auth-service/src/main/resources"

cat > "$BASE/infrastructure/auth-service/pom.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>bf.rnc</groupId>
        <artifactId>rnc-platform</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>
    <artifactId>auth-service</artifactId>
    <name>RNC :: Infrastructure :: Auth Service</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>bf.rnc</groupId>
            <artifactId>common-lib</artifactId>
        </dependency>
        <dependency>
            <groupId>bf.rnc</groupId>
            <artifactId>common-security</artifactId>
        </dependency>
        <dependency>
            <groupId>bf.rnc</groupId>
            <artifactId>common-observability</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
EOF

cat > "$BASE/infrastructure/auth-service/src/main/java/bf/rnc/infrastructure/auth/AuthServiceApplication.java" << 'EOF'
package bf.rnc.infrastructure.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

/**
 * Service d'authentification RNC.
 * Wraps Keycloak avec des endpoints métier (registrement citoyen, MFA OTP SMS, etc.).
 */
@SpringBootApplication
@EnableEurekaClient
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
EOF

cat > "$BASE/infrastructure/auth-service/src/main/java/bf/rnc/infrastructure/auth/controller/AuthController.java" << 'EOF'
package bf.rnc.infrastructure.auth.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints d'authentification RNC.
 * Délègue à Keycloak via Admin Client pour la gestion des utilisateurs.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> request) {
        log.info("Registration request for: {}", request.get("phoneNumber"));
        // TODO: Create Keycloak user, send OTP SMS verification
        return ResponseEntity.ok(Map.of("status", "OTP_SENT", "message", "Code OTP envoyé par SMS"));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyOtp(@RequestBody Map<String, Object> request) {
        log.info("OTP verification for: {}", request.get("phoneNumber"));
        // TODO: Validate OTP, activate Keycloak user
        return ResponseEntity.ok(Map.of("status", "VERIFIED", "message", "Compte activé"));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, Object> request) {
        // TODO: Proxy to Keycloak token endpoint
        return ResponseEntity.ok(Map.of("status", "AUTHENTICATED"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of("status", "REFRESHED"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String auth) {
        // TODO: Revoke token in Keycloak
        return ResponseEntity.noContent().build();
    }
}
EOF

cat > "$BASE/infrastructure/auth-service/src/main/resources/application.yml" << 'EOF'
server:
  port: 8091

spring:
  application:
    name: auth-service
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_URL:http://localhost:8090}/realms/rnc

keycloak:
  server-url: ${KEYCLOAK_URL:http://localhost:8090}
  realm: rnc
  admin-client-id: ${KEYCLOAK_ADMIN_CLIENT:admin-cli}
  admin-client-secret: ${KEYCLOAK_ADMIN_SECRET:}

eureka:
  client:
    serviceUrl:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}
  instance:
    preferIpAddress: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
EOF

echo "[OK] Auth service created"
echo ""
echo "All infrastructure services created successfully!"
