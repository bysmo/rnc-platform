#!/bin/bash
# Generate all 12 Trust microservices with Spring Boot 3.4 skeleton
set -e
BASE="/home/z/my-project/rnc/rnc-platform/services"

# Service definitions: name|className|port|description
SERVICES=(
  "trust-id|TrustIdService|8081|Identité financière numérique — KYC, gestion Citoyens, CIN Burkina Faso, validation biométrique"
  "trust-score|TrustScoreService|8082|Réputation financière nationale — Trust Score dynamique, algorithme explicable, audit régulier"
  "trust-qr|TrustQrService|8083|Paiement par QR Code Confiance — génération, scan, autorisation, plafonds"
  "trust-credit|TrustCreditService|8084|Nano-crédit instantané — demande, analyse risque, déblocage, remboursement"
  "trust-escrow|TrustEscrowService|8085|Compte d'affectation des financements — réservation, déblocage progressif, validation livraison"
  "trust-school|TrustSchoolService|8086|Financement scolaire — écoles partenaires, frais de scolarité, déblocage direct"
  "trust-health|TrustHealthService|8087|Financement santé — centres de santé, pharmacies, urgences médicales"
  "trust-farming|TrustFarmingService|8088|Financement agricole — coopératives, intrants, semences, calendrier saisonnier"
  "trust-insurance|TrustInsuranceService|8089|Micro-assurance — couverture crédit, santé, récolte, primes indexées"
  "trust-debt|TrustDebtService|8090|Reconnaissance de dette entre particuliers — consentement, horodatage, rappels"
  "trust-collect|TrustCollectService|8091|Recouvrement amiable automatisé — rappels, escalade, négociation"
  "trust-merchant|TrustMerchantService|8092|Gestion des fournisseurs partenaires — onboarding, agrément, QR codes"
)

for entry in "${SERVICES[@]}"; do
  IFS='|' read -r name className port description <<< "$entry"
  svc_dir="$BASE/$name"
  pkg_dir="$svc_dir/src/main/java/bf/rnc/services/$(echo $name | tr '-' '/')"
  pkg_name="bf.rnc.services.$(echo $name | tr '-' '.')"
  res_dir="$svc_dir/src/main/resources"
  db_dir="$res_dir/db/migration"

  mkdir -p "$pkg_dir/controller" "$pkg_dir/service" "$pkg_dir/repository" "$pkg_dir/entity" "$pkg_dir/dto" "$pkg_dir/mapper" "$pkg_dir/config" "$db_dir"

  # pom.xml
  cat > "$svc_dir/pom.xml" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>bf.rnc</groupId>
        <artifactId>rnc-platform</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>
    <artifactId>$name</artifactId>
    <name>RNC :: Services :: $className</name>
    <description>$description</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-config</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>\${springdoc.version}</version>
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
            <artifactId>common-data</artifactId>
        </dependency>
        <dependency>
            <groupId>bf.rnc</groupId>
            <artifactId>common-events</artifactId>
        </dependency>
        <dependency>
            <groupId>bf.rnc</groupId>
            <artifactId>common-observability</artifactId>
        </dependency>
        <dependency>
            <groupId>bf.rnc</groupId>
            <artifactId>common-test</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>com.google.cloud.tools</groupId>
                <artifactId>jib-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
EOF

  # Application class
  cat > "$pkg_dir/${className}Application.java" << EOF
package $pkg_name;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * RNC — $className
 * $description
 */
@SpringBootApplication(scanBasePackages = {"$pkg_name", "bf.rnc.common"})
@EnableFeignClients
public class ${className}Application {
    public static void main(String[] args) {
        SpringApplication.run(${className}Application.class, args);
    }
}
EOF

  # Default controller (health/info)
  cat > "$pkg_dir/controller/${className}Controller.java" << EOF
package ${pkg_name}.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ${pkg_name}.service.${className}Service;

/**
 * REST controller — endpoints métier du microservice ${name}.
 */
@RestController
@RequestMapping("/api/v1/${name#trust-}")
@RequiredArgsConstructor
public class ${className}Controller {

    private final ${className}Service service;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\\"service\\":\\"$name\\",\\"status\\":\\"UP\\"}");
    }
}
EOF

  # Service
  cat > "$pkg_dir/service/${className}Service.java" << EOF
package ${pkg_name}.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service métier — logique du microservice ${name}.
 */
@Slf4j
@Service
public class ${className}Service {

    // TODO: implémenter la logique métier
}
EOF

  # application.yml
  cat > "$res_dir/application.yml" << EOF
server:
  port: $port

spring:
  application:
    name: $name
  config:
    import: optional:configserver:\${CONFIG_SERVER_URL:http://localhost:8888}
  datasource:
    url: jdbc:postgresql://\${POSTGRES_HOST:localhost}:\${POSTGRES_PORT:5432}/\${POSTGRES_DB:$name}
    username: \${POSTGRES_USER:rnc}
    password: \${POSTGRES_PASSWORD:rnc}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        jdbc.lob.non_contextual_creation: true
        format_sql: false
    open-in-view: false
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: \${KEYCLOAK_URL:http://localhost:8090}/realms/rnc
          jwk-set-uri: \${KEYCLOAK_URL:http://localhost:8090}/realms/rnc/protocol/openid-connect/certs
  kafka:
    bootstrap-servers: \${KAFKA_URL:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      properties:
        spring.json.add.type.headers: false
    consumer:
      group-id: $name
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "bf.rnc.*"
  rabbitmq:
    host: \${RABBITMQ_HOST:localhost}
    port: \${RABBITMQ_PORT:5672}
    username: \${RABBITMQ_USER:rnc}
    password: \${RABBITMQ_PASSWORD:rnc}

eureka:
  client:
    serviceUrl:
      defaultZone: \${EUREKA_URL:http://localhost:8761/eureka/}
    healthcheck:
      enabled: true
  instance:
    preferIpAddress: true
    leaseRenewalIntervalInSeconds: 10
    leaseExpirationDurationInSeconds: 30

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,env,refresh
  endpoint:
    health:
      show-details: when-authorized
  health:
    circuitbreakers:
      enabled: true

resilience4j:
  circuitbreaker:
    instances:
      default:
        registerHealthIndicator: true
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
  retry:
    instances:
      default:
        maxAttempts: 3
        waitDuration: 2s

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    enabled: true

rnc:
  service:
    name: $name
    description: "$description"
EOF

  # bootstrap.yml (for config server import)
  cat > "$res_dir/bootstrap.yml" << EOF
spring:
  application:
    name: $name
  cloud:
    config:
      fail-fast: false
      retry:
        max-attempts: 6
        initial-interval: 1000
        max-interval: 3000
        multiplier: 1.2
EOF

  # V1__init.sql migration placeholder
  cat > "$db_dir/V1__init_schema.sql" << EOF
-- ============================================================
-- $name — Schéma initial
-- ============================================================
-- TODO: créer les tables spécifiques à ce microservice
-- Conventions RNC:
--   * toute table a: id (UUID), created_at, updated_at, created_by, updated_by, deleted, version
--   * utilisation de jsonb pour les données flexibles
--   * index systématiques sur les clés étrangères et les colonnes filtrées
-- ============================================================

CREATE SCHEMA IF NOT EXISTS ${name//-/_};

-- Exemple:
-- CREATE TABLE ${name//-/_}.entity_example (
--     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
--     name VARCHAR(255) NOT NULL,
--     status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
--     metadata JSONB,
--     created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
--     updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
--     created_by VARCHAR(100),
--     updated_by VARCHAR(100),
--     deleted BOOLEAN NOT NULL DEFAULT FALSE,
--     version BIGINT NOT NULL DEFAULT 0
-- );
-- CREATE INDEX idx_entity_example_status ON ${name//-/_}.entity_example(status) WHERE NOT deleted;
-- CREATE INDEX idx_entity_example_created_at ON ${name//-/_}.entity_example(created_at);
EOF

  # application-test.yml
  mkdir -p "$svc_dir/src/test/resources"
  cat > "$svc_dir/src/test/resources/application-test.yml" << EOF
spring:
  datasource:
    url: jdbc:tc:postgresql:16-alpine:///test
  jpa:
    hibernate:
      ddl-auto: create-drop
  flyway:
    enabled: false
  kafka:
    bootstrap-servers: \${spring.embedded.kafka.brokers}
EOF

  # Test class
  test_dir="$svc_dir/src/test/java/bf/rnc/services/$(echo $name | tr '-' '/')"
  mkdir -p "$test_dir"
  cat > "$test_dir/${className}ApplicationTest.java" << EOF
package ${pkg_name};

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ${className}ApplicationTest {

    @Test
    void contextLoads() {
        // Vérifie que le contexte Spring démarre correctement
    }
}
EOF

  echo "[OK] $name (port $port)"
done

echo ""
echo "All 12 Trust microservices created!"
