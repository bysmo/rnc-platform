# Trust ID — Identité Financière Numérique

> Service responsable de l'identité numérique des citoyens burkinabè dans le RNC.

## Vue d'ensemble

Trust ID gère le cycle de vie complet de l'identité d'un citoyen : inscription, vérification OTP, KYC (CIN), screening sanctions (LCB-FT), consentement (Loi 010-2004/AN), et gestion administrative du compte.

## Architecture

```
src/main/java/bf/rnc/services/trust/id/
├── TrustIdServiceApplication.java   # Point d'entrée Spring Boot
├── client/
│   ├── KeycloakAdminClient.java     # Création utilisateurs Keycloak (avec mode mock)
│   └── SmsOtpService.java           # Envoi OTP SMS (mock console / future API opérateurs)
├── config/
│   └── OpenApiConfig.java           # Swagger / OpenAPI 3
├── controller/
│   └── CitizenController.java       # REST API endpoints
├── dto/
│   ├── RegistrationRequest.java
│   ├── RegistrationResponse.java
│   ├── OtpVerificationRequest.java
│   ├── KycSubmissionRequest.java
│   ├── KycDecisionRequest.java
│   └── CitizenProfileResponse.java
├── entity/
│   ├── Citizen.java                 # Entité principale (données chiffrées)
│   ├── KycDocument.java             # Documents justificatifs
│   ├── OtpVerification.java         # Codes OTP (jamais en clair)
│   ├── SanctionsScreening.java      # Audit LCB-FT
│   ├── ConsentHistory.java          # Preuve consentement
│   ├── KycStatus.java               # Enum
│   ├── AccountStatus.java           # Enum
│   ├── PepStatus.java               # Enum
│   ├── DocumentType.java            # Enum
│   ├── DocumentVerificationStatus.java
│   ├── ConsentType.java             # Enum
│   └── OtpPurpose.java              # Enum
├── repository/
│   ├── CitizenRepository.java
│   ├── OtpVerificationRepository.java
│   ├── KycDocumentRepository.java
│   ├── ConsentHistoryRepository.java
│   └── SanctionsScreeningRepository.java
└── service/
    ├── CitizenService.java          # Logique métier principale
    ├── HashingService.java          # SHA-256 avec pepper (recherche exacte)
    ├── OtpCodeGenerator.java        # Génération codes OTP 6 chiffres
    └── SanctionsScreeningService.java # Screening sanctions (mock)
```

## API REST

| Méthode | Endpoint | Rôle requis | Description |
|---------|----------|-------------|-------------|
| POST | `/api/v1/identity/register` | Public | Inscription citoyen + envoi OTP |
| POST | `/api/v1/identity/verify-otp` | Public | Vérification OTP + activation compte |
| POST | `/api/v1/identity/resend-otp` | Public | Renvoi OTP (rate limited) |
| POST | `/api/v1/identity/kyc/submit` | CITIZEN | Soumission CIN pour validation |
| POST | `/api/v1/identity/kyc/validate` | ADMIN, BANK_AGENT | Validation/rejet KYC |
| GET | `/api/v1/identity/profile/{ref}` | AUTH | Consultation profil citoyen |
| GET | `/api/v1/identity/me` | CITIZEN | Mon propre profil (depuis JWT) |
| GET | `/api/v1/identity/search` | ADMIN, BANK_AGENT, AUDITOR | Recherche citoyens |
| POST | `/api/v1/identity/{id}/suspend` | ADMIN | Suspendre compte |
| POST | `/api/v1/identity/{id}/reactivate` | ADMIN | Réactiver compte |
| POST | `/api/v1/identity/{id}/close` | ADMIN | Clôturer + anonymiser (droit à l'oubli) |

## Sécurité

- **Chiffrement au repos** : AES-256-GCM pour données personnelles (`HsmEncryptionService`)
- **Hash avec pepper** : SHA-256 pour recherche exacte (téléphone, CIN, email)
- **OTP jamais en clair** : hash SHA-256 stocké en base
- **Rate limiting OTP** : 1/min, 5/heure max
- **Consentement obligatoire** : Loi 010-2004/AN tracé dans `consent_history`
- **Audit immuable** : `@Auditable` AOP aspect publie vers Kafka
- **Droit à l'oubli** : `closeAccount()` anonymise les données

## Configuration

Variables principales (voir `application.yml`) :

```yaml
rnc:
  otp:
    ttl-minutes: 10              # Durée validité OTP
    rate-limit-minutes: 1        # Intervalle minimal entre OTP
    rate-limit-max-per-hour: 5   # Max OTP/heure
  sms:
    mock: true                   # true = console (dev), false = vraie passerelle
    provider: mock               # mock | orange | moov | telecel
  keycloak:
    mock: true                   # true = stub (dev), false = vraie intégration
  security:
    pepper: ${RNC_SECURITY_PEPPER}  # Secret à stocker dans Vault/HSM
```

## Tests

### Tests unitaires (`CitizenServiceTest`)

Couverture de la logique métier isolée (mocks) :
- Inscription réussie, refus sans consentement, doublon téléphone, rate limit OTP
- Soumission KYC, refus si téléphone non vérifié, CIN déjà utilisée
- Validation KYC par agent, rejet sans raison
- Suspension, clôture (anonymisation), réactivation conditionnelle

### Tests d'intégration (`TrustIdIntegrationTest`)

Avec PostgreSQL réel via Testcontainers :
- Cycle complet inscription → OTP → KYC → validation
- Vérification que les données sont chiffrées en base
- Vérification que le hash est déterministe (recherche exacte)
- Vérification que le chiffrement est non déterministe (IV aléatoire)
- Clôture → anonymisation effective

### Lancer les tests

```bash
# Tests unitaires seulement (rapide)
mvn -pl services/trust-id test -Dtest=CitizenServiceTest

# Tests d'intégration (nécessite Docker pour Testcontainers)
mvn -pl services/trust-id test -Dtest=TrustIdIntegrationTest

# Tous les tests
mvn -pl services/trust-id test
```

## Démarrage

```bash
# 1. Démarrer PostgreSQL (via docker-compose à la racine)
docker compose up -d postgres keycloak

# 2. Lancer le service
mvn -pl services/trust-id spring-boot:run

# 3. Vérifier
curl http://localhost:8081/actuator/health
curl http://localhost:8081/swagger-ui.html
```

## Points d'extension (TODO MVP → Production)

1. **KeycloakAdminClient** : remplacer les stubs par l'intégration réelle `keycloak-admin-client`
2. **SmsOtpService** : intégrer API Orange/Moov/Telecel Money SMS
3. **SanctionsScreeningService** : intégrer listes ONU/OFAC/UE réelles + base PEP
4. **Stockage documents KYC** : intégrer S3/MinIO pour `storage_url` des `KycDocument`
5. **OCR CIN** : future amélioration pour pré-remplir le KYC depuis photo CIN
6. **Biométrie** : empreinte digitale / faciale via Flutter `local_auth`
7. **HSM** : brancher `HsmEncryptionService` sur HSM physique en production
