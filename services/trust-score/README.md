# Trust Score — Réputation Financière Nationale

> Service responsable du Trust Score des citoyens : algorithme dynamique, explicable et audité.

## Vue d'ensemble

Trust Score calcule et maintient un score entre **0 et 1000** pour chaque citoyen RNC. Le score évolue en temps réel en fonction des événements financiers (remboursements, retards, dettes honorées, etc.). L'algorithme est **entièrement explicable** : chaque variation est tracée et le citoyen peut consulter l'historique.

## Niveaux de score

| Niveau | Plage | Décision crédit | Montant max recommandé |
|--------|-------|-----------------|------------------------|
| 🟢 EXCELLENT | 800-1000 | Crédit automatique | 500 000 XOF |
| 🔵 GOOD | 650-799 | Crédit automatique | 200 000 XOF |
| 🟡 FAIR | 500-649 | Analyse manuelle | 50 000 XOF |
| 🟠 LOW | 300-499 | Micro-montants | 10 000 XOF |
| 🔴 CRITICAL | 0-299 | Aucun crédit | 0 XOF |

## Événements de score

### Événements positifs

| Événement | Impact |
|-----------|--------|
| `CREDIT_REPAID_ON_TIME` | +20 |
| `CREDIT_REPAID_EARLY` | +25 |
| `DEBT_HONORED` | +15 |
| `DEBT_HONORED_EARLY` | +20 |
| `KYC_VERIFIED` | +30 |
| `MOBILE_MONEY_ACTIVE` | +10 |
| `LONG_ACCOUNT_HISTORY` | +15 |

### Événements négatifs

| Événement | Impact |
|-----------|--------|
| `CREDIT_LATE_PAYMENT_1D` (1-7j) | -10 |
| `CREDIT_LATE_PAYMENT_8D` (8-30j) | -30 |
| `CREDIT_DEFAULT` (>30j) | -100 |
| `DEBT_OVERDUE` | -25 |
| `DEBT_DEFAULTED` | -50 |
| `KYC_REJECTED` | -20 |
| `FRAUD_SUSPECTED` | -150 |
| `ACCOUNT_SUSPENDED` | -50 |

## API REST

| Méthode | Endpoint | Rôle | Description |
|---------|----------|------|-------------|
| GET | `/api/v1/score/{citizenId}` | AUTH | Consulter le score |
| GET | `/api/v1/score/me` | CITIZEN | Mon propre score |
| GET | `/api/v1/score/{citizenId}/events` | AUTH | Historique événements |
| GET | `/api/v1/score/{citizenId}/eligibility` | AUTH | Éligibilité crédit auto |
| POST | `/api/v1/score/events` | SYSTEM, ADMIN | Enregistrer un événement |
| POST | `/api/v1/score/initialize` | SYSTEM, ADMIN | Initialiser un score |
| POST | `/api/v1/score/appeals` | CITIZEN | Déposer une contestation |
| GET | `/api/v1/score/appeals/pending` | ADMIN | Contestations en attente |
| GET | `/api/v1/score/appeals/{citizenId}` | AUTH | Contestations d'un citoyen |
| POST | `/api/v1/score/appeals/decision` | ADMIN | Décider une contestation |
| POST | `/api/v1/score/adjust` | ADMIN | Ajustement manuel |
| GET | `/api/v1/score/analytics` | ADMIN, AUDITOR | Analytics agrégés |
| GET | `/api/v1/score/critical` | ADMIN | Scores critiques |

## Architecture

```
src/main/java/bf/rnc/services/trust/score/
├── TrustScoreServiceApplication.java
├── config/
│   └── OpenApiConfig.java
├── controller/
│   └── ScoreController.java           # 13 endpoints REST
├── dto/
│   ├── ScoreResponse.java             # Avec détail des facteurs
│   ├── ScoreEventRequest.java         # Événement entrant
│   ├── ScoreEventResponse.java
│   ├── ScoreAppealRequest.java        # Contestation citoyen
│   └── AppealDecisionRequest.java     # Décision agent
├── entity/
│   ├── Score.java                     # Score courant (1/citoyen)
│   ├── ScoreEvent.java                # Événement immuable
│   ├── ScoreHistory.java              # Historique audit
│   ├── ScoreAppeal.java               # Contestation
│   ├── ScoreFactorConfig.java         # Config facteurs
│   ├── ScoreLevel.java                # Enum 5 niveaux
│   ├── ScoreEventType.java            # Enum 15 événements
│   ├── ScoreHistorySource.java        # Enum
│   └── AppealStatus.java              # Enum
├── repository/
│   ├── ScoreRepository.java           # 5 méthodes custom
│   ├── ScoreEventRepository.java      # 5 méthodes custom
│   ├── ScoreHistoryRepository.java
│   ├── ScoreAppealRepository.java
│   └── ScoreFactorConfigRepository.java
└── service/
    └── TrustScoreService.java         # Logique métier principale
```

## Conformité

- **Transparence algorithmique** : chaque citoyen peut consulter l'historique détaillé de son score
- **Contestation possible** : tout citoyen peut déposer une `ScoreAppeal` pour révision humaine
- **Audit immuable** : `score_history` est append-only, traçant tous les changements
- **Configuration auditable** : les poids des facteurs sont en base (`score_factors_config`)
- **Idempotence** : les événements dupliqués (même référence) sont ignorés

## Tests

### Tests unitaires (`TrustScoreServiceTest`)
- Initialisation score (succès + refus doublon)
- Événements positifs/négatifs (augmentation + clampage)
- Idempotence (événement dupliqué ignoré)
- Consultation + éligibilité crédit automatique
- Contestations (soumission + approbation)
- Ajustement manuel (valide + hors bornes)

### Tests d'intégration (`TrustScoreIntegrationTest`)
- Cycle complet : init → événements → ajustement
- Idempotence avec DB réelle
- Clampage [0, 1000]
- Contestation bout-en-bout
- Analytics agrégés
- Historique pour explicabilité
- Récupération scores critiques

### Lancer les tests

```bash
# Tests unitaires seulement
mvn -pl services/trust-score test -Dtest=TrustScoreServiceTest

# Tests d'intégration (nécessite Docker)
mvn -pl services/trust-score test -Dtest=TrustScoreIntegrationTest

# Tous
mvn -pl services/trust-score test
```

## Démarrage

```bash
# 1. Démarrer PostgreSQL
docker compose up -d postgres

# 2. Lancer le service
mvn -pl services/trust-score spring-boot:run

# 3. Vérifier
curl http://localhost:8082/actuator/health
curl http://localhost:8082/swagger-ui.html
```

## Intégration avec autres services

Trust Score consomme des événements publiés par :

| Service source | Événement | Trust Score impact |
|----------------|-----------|-------------------|
| Trust Credit | Crédit remboursé à temps | `CREDIT_REPAID_ON_TIME` (+20) |
| Trust Credit | Crédit remboursé en avance | `CREDIT_REPAID_EARLY` (+25) |
| Trust Credit | Retard 1-7 jours | `CREDIT_LATE_PAYMENT_1D` (-10) |
| Trust Credit | Retard 8-30 jours | `CREDIT_LATE_PAYMENT_8D` (-30) |
| Trust Credit | Défaut >30 jours | `CREDIT_DEFAULT` (-100) |
| Trust Debt | Dette honorée | `DEBT_HONORED` (+15) |
| Trust Debt | Dette en retard | `DEBT_OVERDUE` (-25) |
| Trust Debt | Dette non honorée | `DEBT_DEFAULTED` (-50) |
| Trust ID | KYC vérifié | `KYC_VERIFIED` (+30) |
| Trust ID | KYC rejeté | `KYC_REJECTED` (-20) |
| Trust ID | Compte suspendu | `ACCOUNT_SUSPENDED` (-50) |

L'intégration se fait via **Kafka** (topic `rnc.score.events`) ou appel **REST direct** (`POST /api/v1/score/events`).
