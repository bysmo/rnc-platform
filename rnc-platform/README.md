# 🇧🇫 RNC — Réseau National de Confiance

> **Infrastructure numérique de confiance pour démocratiser le nano-crédit, protéger les transactions et accélérer l'inclusion financière au Burkina Faso.**

[![Build Status](https://img.shields.io/github/actions/workflow/status/rnc-burkina-faso/rnc-platform/ci-cd.yml)](.github/workflows/ci-cd.yml)
[![Coverage](https://codecov.io/gh/rnc-burkina-faso/rnc-platform/branch/main/graph/badge.svg)](https://codecov.io/gh/rnc-burkina-faso/rnc-platform)
[![License](https://img.shields.io/badge/license-Proprietary-red.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-green.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2024-green.svg)](https://spring.io/projects/spring-cloud)

---

## 📖 Vue d'ensemble

Le RNC n'est **pas** une banque, ni une microfinance, ni un opérateur Mobile Money. C'est une **infrastructure numérique de confiance** reliant l'ensemble des acteurs financiers du Burkina Faso : banques, institutions de microfinance, opérateurs Mobile Money, assurances, fournisseurs agréés, écoles, centres de santé, coopératives agricoles et particuliers.

Chaque citoyen dispose d'un **Compte Confiance** qui devient son passeport financier portable.

### 🎯 Objectifs

- Démocratiser l'accès au nano-crédit
- Réduire fortement le risque de crédit
- Empêcher le détournement des financements (crédit affecté / escrow)
- Créer une réputation financière nationale (Trust Score)
- Protéger les prêts entre particuliers
- Favoriser l'inclusion financière en milieu rural

---

## 🏗️ Architecture

### Stack technique

| Composant | Technologie | Version |
|-----------|-------------|---------|
| Langage | Java | 21 LTS |
| Framework | Spring Boot | 3.4 |
| Microservices | Spring Cloud | 2024 |
| Service Discovery | Netflix Eureka | — |
| API Gateway | Spring Cloud Gateway | — |
| IAM | Keycloak (OIDC/OAuth2) | 25.0 |
| Base de données | PostgreSQL | 16 |
| Event streaming | Apache Kafka | 3.7 |
| Message broker | RabbitMQ | 3.13 |
| Migrations DB | Flyway | 10.x |
| Caching | Redis | 7 |
| Mobile | Flutter | 3.x |
| Observabilité | Prometheus / Grafana / Loki / Tempo | — |
| Conteneurisation | Docker + Docker Compose | — |
| Orchestration | Kubernetes (Helm) | — |

### Microservices (12 modules métier + 4 modules infrastructure)

| # | Service | Port | Responsabilité |
|---|---------|------|----------------|
| 🛰️ 1 | `eureka-server` | 8761 | Service discovery |
| 🛰️ 2 | `config-server` | 8888 | Configuration centralisée |
| 🛰️ 3 | `api-gateway` | 8080 | Gateway unique + rate limiting |
| 🛰️ 4 | `auth-service` | 8091 | Wrapper Keycloak (register, OTP, MFA) |
| 💼 5 | `trust-id` | 8081 | Identité financière (KYC, LCB-FT) |
| 📊 6 | `trust-score` | 8082 | Trust Score dynamique explicable |
| 📲 7 | `trust-qr` | 8083 | QR Code Confiance |
| 💰 8 | `trust-credit` | 8084 | Nano-crédit instantané |
| 🔒 9 | `trust-escrow` | 8085 | Crédit affecté + déblocage progressif |
| 🎓 10 | `trust-school` | 8086 | Financement scolaire |
| 🏥 11 | `trust-health` | 8087 | Financement santé |
| 🌾 12 | `trust-farming` | 8088 | Financement agricole saisonnier |
| 🛡️ 13 | `trust-insurance` | 8089 | Micro-assurance |
| 📝 14 | `trust-debt` | 8090 | Reconnaissance de dette entre particuliers |
| 📞 15 | `trust-collect` | 8091 | Recouvrement amiable automatisé |
| 🏪 16 | `trust-merchant` | 8092 | Gestion fournisseurs partenaires |

### Modules communs réutilisables

| Module | Rôle |
|--------|------|
| `common-lib` | DTOs, exceptions, value objects (Money), utilitaires |
| `common-security` | OAuth2 Keycloak, audit AOP, chiffrement HSM |
| `common-data` | JPA, Flyway, audit fields, auditor aware |
| `common-events` | Kafka topics, RabbitMQ queues, Outbox pattern |
| `common-observability` | Micrometer, OpenTelemetry, Loki appender |
| `common-test` | Testcontainers, fixtures, ArchUnit |

---

## 🚀 Démarrage rapide

### Prérequis

- Java 21 (`java -version`)
- Maven 3.9+ (`mvn -version`)
- Docker + Docker Compose
- Git

### 1. Cloner le dépôt

```bash
git clone git@gitlab.rnc.bf:rnc/platform.git
cd platform
cp .env.example .env
# Éditer .env si nécessaire
```

### 2. Démarrer l'infrastructure

```bash
docker compose --profile infra up -d
```

Cela démarre : PostgreSQL, Redis, Keycloak, Eureka, Config Server, Kafka, RabbitMQ.

Vérifier :
- Keycloak: http://localhost:8090 (admin / admin)
- Eureka: http://localhost:8761
- Kafka UI: http://localhost:8098

### 3. Construire et lancer les microservices

```bash
mvn clean install -DskipTests

# Lancer tous les services (dans des terminaux séparés)
cd infrastructure/api-gateway && mvn spring-boot:run
cd services/trust-id && mvn spring-boot:run
cd services/trust-credit && mvn spring-boot:run
# ... ou via docker compose --profile services up -d
```

### 4. Vérifier

- API Gateway: http://localhost:8080
- Trust Credit Swagger: http://localhost:8084/swagger-ui.html
- Grafana: http://localhost:3001 (admin / admin)

---

## 📁 Structure du dépôt

```
rnc-platform/
├── pom.xml                         # Parent POM multi-modules
├── docker-compose.yml              # Environnement de développement
├── .env.example                    # Variables d'environnement modèle
├── .github/workflows/ci-cd.yml     # Pipeline CI/CD GitHub Actions
│
├── common/                         # Librairies communes réutilisables
│   ├── common-lib/
│   ├── common-security/
│   ├── common-data/
│   ├── common-events/
│   ├── common-observability/
│   └── common-test/
│
├── infrastructure/                 # Services transverses
│   ├── eureka-server/              # Service discovery
│   ├── config-server/              # Configuration centralisée
│   ├── api-gateway/                # Gateway + rate limiting
│   └── auth-service/               # Auth wrapper (Keycloak)
│
├── services/                       # 12 microservices métier Trust*
│   ├── trust-id/                   # Identité financière
│   ├── trust-score/                # Trust Score
│   ├── trust-qr/                   # QR Code Confiance
│   ├── trust-credit/               # Nano-crédit
│   ├── trust-escrow/               # Crédit affecté
│   ├── trust-school/               # Financement scolaire
│   ├── trust-health/               # Financement santé
│   ├── trust-farming/              # Financement agricole
│   ├── trust-insurance/            # Micro-assurance
│   ├── trust-debt/                 # Dette entre particuliers
│   ├── trust-collect/              # Recouvrement
│   └── trust-merchant/             # Fournisseurs partenaires
│
├── deploy/                         # Déploiement production
│   ├── k8s/                        # Manifests Kubernetes
│   └── helm/                       # Charts Helm
│
├── docker/                         # Configs Docker
│   ├── postgres/
│   ├── keycloak/
│   ├── prometheus/
│   ├── grafana/
│   └── tempo/
│
├── docs/                           # Documentation
│   ├── adr/                        # Architecture Decision Records
│   ├── diagrams/                   # Diagrammes C4 (PlantUML)
│   └── CONCEPTION.md               # Document de conception complet
│
├── mobile/                         # Application mobile Flutter
│
└── scripts/                        # Scripts utilitaires
```

---

## 🔐 Sécurité & Conformité

Le RNC intègre dès la conception les contraintes réglementaires suivantes :

| Conformité | Implémentation |
|------------|----------------|
| **BCEAO** (EMI/PSV) | Suivi des directives EMI, plafonds réglementaires, traçabilité |
| **Loi 010-2004/AN** (données personnelles Burkina Faso) | Chiffrement au repos, consentement explicite, droit à l'oubli |
| **Audit immuable** | Kafka append-only topic `rnc.audit.events` + hash blockchain |
| **Chiffrement HSM** | AES-256-GCM avec délégation HSM PKCS#11 en production |
| **KYC / LCB-FT** | Vérification CIN, screening sanctions, PEP, déclarations TRACFIN |

Voir [`docs/SECURITY.md`](docs/SECURITY.md) pour le détail.

---

## 🧪 Tests

```bash
# Tests unitaires
mvn test

# Tests d'intégration (Testcontainers)
mvn verify -Pintegration

# Couverture JaCoCo
mvn jacoco:report
# Ouvrir target/site/jacoco/index.html
```

---

## 📊 Observabilité

| Stack | URL | Identifiants |
|-------|-----|--------------|
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3001 | admin / admin |
| Loki (logs) | via Grafana | — |
| Tempo (traces) | via Grafana | — |
| Kafka UI | http://localhost:8098 | — |
| RabbitMQ Mgmt | http://localhost:15672 | rnc / rnc |
| Keycloak Admin | http://localhost:8090 | admin / admin |
| Eureka | http://localhost:8761 | — |

---

## 🗺️ Roadmap

- ✅ **Phase 1 — Conception** (ce dépôt)
- 🚧 **Phase 2 — MVP** : trust-id, trust-score, trust-credit, trust-qr, trust-debt
- ⏳ **Phase 3 — Pilote** (ville + communes rurales)
- ⏳ **Phase 4 — Déploiement national**
- ⏳ **Phase 5 — Extension UEMOA**

Voir [`docs/ROADMAP.md`](docs/ROADMAP.md).

---

## 🤝 Contribution

Voir [`CONTRIBUTING.md`](CONTRIBUTING.md).

## 📄 Licence

Propriétaire — © République du Burkina Faso. Voir [`LICENSE`](LICENSE).

## 📞 Contact

- **Email** : contact@rnc.bf
- **Site web** : https://rnc.bf
- **Adresse** : Ouagadougou, Burkina Faso
