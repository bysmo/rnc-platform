# ADR-002 : Architecture microservices avec Eureka + Spring Cloud Gateway

- **Statut** : Accepté
- **Date** : 2025-06-30
- **Décideurs** : Équipe Architecture RNC

## Contexte

Le RNC comporte 12 modules métier distincts (Trust ID, Trust Score, Trust Credit, etc.) avec des cycles de vie et des contraintes de scalabilité différents. Une architecture monolithe poserait des problèmes :
- Déploiement atomique lourd ;
- Scalabilité globale (un service gourmand pénalise tout) ;
- Équipes parallélisées difficiles ;
- Couplage fort entre modules.

## Décision

Architecture microservices avec :
- **Service Discovery** : Netflix Eureka (intégré Spring Cloud)
- **API Gateway** : Spring Cloud Gateway (reactive, basé sur Netty)
- **Config centralisée** : Spring Cloud Config Server
- **Communication inter-services** : OpenFeign (synchrone REST) + Kafka (asynchrone events)
- **Circuit breaker** : Resilience4j

## Justification

### Pourquoi Eureka plutôt que Consul ou K8s natif ?

| Critère | Eureka | Consul | K8s natif |
|---------|--------|--------|-----------|
| Intégration Spring | ✅ Native | ⚠️ Config | ⚠️ Aucune |
| Complexité ops | ✅ Faible | ⚠️ Moyenne | ❌ Élevée |
| Dépendance K8s | ❌ Aucune | ❌ Aucune | ✅ Totale |
| Multi-datacenter | ⚠️ Limité | ✅ Natif | ✅ Via federation |
| Adapté Burkina Faso | ✅ Oui (simplicité) | ⚠️ Surdimensionné | ❌ Trop complexe |

### Pourquoi Spring Cloud Gateway plutôt que Kong ?

- Gateway réactif non bloquant (performances équivalentes)
- Configuration Java/YAML pure (pas de Lua à apprendre)
- Intégration native avec Spring Security OAuth2
- Pas de service externe à opérer

## Conséquences

- **Positives** : scalabilité indépendante, déploiements isolés, résilience accrue, équipe parallélisée.
- **Négatives** : complexité opérationnelle (16 services à monitorer), latence réseau entre services, cohérence transactionnelle distribuée à gérer via saga/outbox.
- **Mitigations** : observabilité complète (Prometheus + Loki + Tempo), pattern Outbox pour la cohérence, documentation ADR stricte.

## Risques et mitigations

| Risque | Probabilité | Impact | Mitigation |
|--------|-------------|--------|------------|
| Défaillance Eureka | Faible | Élevé | Cluster Eureka (3 nœuds) + cache client |
| Latence Gateway | Moyenne | Moyen | Cache Redis + rate limiting |
| Couplage caché via DB partagée | Moyenne | Élevé | 1 DB par service (PostgreSQL multi-schémas) |

## Références

- https://spring.io/projects/spring-cloud-netflix
- https://spring.io/projects/spring-cloud-gateway
- Pattern : Microservices de Sam Newman (O'Reilly, 2nd ed.)
