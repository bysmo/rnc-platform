# ADR-004 : Messagerie asynchrone hybride Kafka + RabbitMQ

- **Statut** : Accepté
- **Date** : 2025-06-30
- **Décideurs** : Équipe Architecture RNC

## Contexte

Les microservices RNC doivent communiquer de manière asynchrone pour :
- **Événements métier** (crédit accordé, score mis à jour, dette signée) → broadcast à plusieurs consommateurs, audit immuable
- **Tâches asynchrones** (envoi SMS, génération rapport, recouvrement) → file FIFO avec retry
- **Notifications** (rappels de paiement, alertes) → file prioritaire

Ces deux besoins ont des sémantiques différentes : event streaming vs work queue.

## Décision

Adoption d'un **mixte Kafka + RabbitMQ** :

| Technologie | Usage RNC |
|-------------|-----------|
| **Apache Kafka** | Event streaming, audit immuable, broadcast events métier, source de vérité pour analytics |
| **RabbitMQ** | Work queues, tâches asynchrones, notifications priorisées, RPC |

### Topics Kafka (event streaming)

```
rnc.audit.events              # Audit immuable (tous les services)
rnc.trust-score.updated       # Score mis à jour (consommateurs: credit, qr, dashboard)
rnc.credit.requested          # Demande de crédit (consommateurs: scoring, escrow)
rnc.credit.approved           # Crédit approuvé (consommateurs: escrow, notification)
rnc.credit.repaid             # Remboursement (consommateurs: score, escrow)
rnc.escrow.released           # Fonds libérés (consommateurs: merchant, notification)
rnc.debt.signed               # Dette signée (consommateurs: score, collect)
rnc.identity.kyc-verified     # KYC validé (consommateurs: credit, score)
```

### Queues RabbitMQ (work queues)

```
rnc.notifications.sms         # Envoi SMS (consommateur: worker SMS gateway)
rnc.notifications.email       # Envoi email
rnc.notifications.push        # Notifications push mobile
rnc.collect.reminders         # Rappels de paiement (consommateur: scheduler)
rnc.collect.escalation        # Escalade recouvrement (consommateur: collector)
rnc.reports.generation        # Génération rapports asynchrones
```

## Justification

### Pourquoi Kafka pour les events métier ?

- **Persistance** : retention 7 jours minimum → replay possible pour retraitement
- **Audit immuable** : topic `rnc.audit.events` en append-only → preuve légale
- **Plusieurs consommateurs** : pattern pub/sub natif
- **Performance** : 100k+ msg/s sur cluster modeste
- **Source d'événements** : event sourcing possible pour Trust Score

### Pourquoi RabbitMQ pour les tâches ?

- **FIFO garantie** : important pour files de notifications
- **Priorités** : queue prioritaire pour alertes critiques
- **Acknowledgments** : pas de perte de messages en cas de crash worker
- **RPC** : pour requêtes synchrones asynchrones (ex: génération PDF)
- **Simple** : pas de notion de consumer group/offset

### Pourquoi ne pas choisir uniquement Kafka ?

- Pas de priorité native sur les messages
- Pas de ack/nack explicite (at-least-once mais pas de "je prends le message X")
- Latence plus élevée pour petits messages isolés
- Complexité ops supérieure

### Pourquoi ne pas choisir uniquement RabbitMQ ?

- Pas de replay historique
- Scalabilité horizontale limitée
- Pas adapté à l'audit immuable long terme

## Conséquences

- **Positives** : chaque technologie utilisée pour son point fort, séparation des préoccupations
- **Négatives** : deux stacks à opérer, deux clients à gérer par service
- **Complexité** : moyenne (KafkaStreams évité, consumers Spring Kafka simples)

## Implémentation

- `common-events` : constantes centralisées (`KafkaTopics`, `RabbitQueues`)
- **Pattern Outbox** : pour publication fiable, une entité `outbox_events` est créée en même temps que la modification métier (même transaction PostgreSQL), puis un poller publie vers Kafka
- **Sérialisation** : JSON via Jackson (inter-opérabilité maximale), pas d'Avro/Protobuf pour simplifier
- **CloudEvents** : enveloppe normalisée pour tous les events

## Risques et mitigations

| Risque | Mitigation |
|--------|------------|
| Double stack ops | Monitoring unifié (Prometheus), dashboards Grafana |
| Message ordering Kafka | Clé par aggregate ID (citizen_id) |
| Perte message RabbitMQ | Acknowledgment manuel + dead-letter queue |
| Outbox non lu | Poller avec healthcheck, alerte si backlog > 1000 |

## Références

- https://kafka.apache.org/documentation/
- https://www.rabbitmq.com/
- Pattern Outbox : https://microservices.io/patterns/data/transactional-outbox.html
- CloudEvents : https://cloudevents.io/
