# ADR-001 : Choix de Spring Boot 3.4 + Spring Cloud 2024 + Java 21

- **Statut** : Accepté
- **Date** : 2025-06-30
- **Décideurs** : Équipe Architecture RNC

## Contexte

Le RNC doit être développé avec une architecture microservices Java. Le choix du socle technique engage le projet sur 5+ années et affecte : coût de formation, support communautaire, performance, sécurité, et compatibilité avec les évolutions futures.

## Décision

Nous adoptons :
- **Java 21 LTS** : dernière version LTS avec support à long terme (sept 2028 minimum), apporte virtual threads (Project Loom), pattern matching, records.
- **Spring Boot 3.4** : dernière version stable, supporte Java 21, compatible GraalVM native images, requiert Jakarta EE 10.
- **Spring Cloud 2024.0** : aligné avec Spring Boot 3.4, dernière fonctionnalité Eureka/Gateway/Config.

## Justification

| Critère | Spring Boot 3.4 | Alternative (3.2) | Alternative (2.7) |
|---------|-----------------|-------------------|-------------------|
| Support Java 21 | ✅ Natif | ⚠️ Partiel | ❌ Non |
| Virtual threads | ✅ Stable | ⚠️ Preview | ❌ Non |
| Fin de support OSS | sept 2027 | nov 2025 | nov 2023 (dépassé) |
| Écosystème | ✅ Très actif | ✅ Actif | ⚠️ Maintenance |
| GraalVM native | ✅ Stable | ✅ Stable | ⚠️ Experimental |

## Conséquences

- **Positives** : performance (virtual threads), modernité syntaxique (records, sealed classes), support à long terme, écosystème à jour.
- **Négatives** : courbe d'apprentissage pour les équipes sur Java 8/11 (mais limitée), certaines librairies anciennes incompatibles (rare).
- **Risques** : aucun majeur identifié — Spring Boot 3.x est mature.

## Alternatives rejetées

- **Spring Boot 2.7** : fin de support commercial proche, dette technique immédiate.
- **Quarkus / Micronaut** : écosystème moins étendu, équipe moins familière, bénéfices (native image) non critiques pour ce projet.
- **Node.js / .NET** : stack différent des compétences de l'équipe, moins adapté aux contraintes de charge transactionnelle type microfinance.

## Références

- https://spring.io/projects/spring-boot
- https://spring.io/projects/spring-cloud
- https://openjdk.org/projects/jdk/21/
