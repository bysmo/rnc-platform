# ADR-005 : Pattern Outbox pour cohérence transactionnelle

- **Statut** : Accepté
- **Date** : 2025-06-30
- **Décideurs** : Équipe Architecture RNC

## Contexte

Dans une architecture microservices, on rencontre fréquemment le besoin suivant :
1. Modifier une entité en base (ex: `UPDATE credits SET status='APPROVED'`)
2. Publier un événement Kafka (`rnc.credit.approved`)

Ces deux opérations **doivent être atomiques**. Sinon :
- Si on publie avant commit DB : message fantôme si rollback DB
- Si on publie après commit DB : message perdu si crash service

Spring `@Transactional` ne peut pas couvrir Kafka + PostgreSQL dans la même transaction (transactions distribuées XA sont trop coûteuses et fragiles).

## Décision

Adoption du **pattern Transactional Outbox** :

1. Une table `outbox_events` est créée dans **chaque** base de microservice
2. Toute modification métier qui doit publier un event écrit une ligne dans `outbox_events` dans la **même transaction PostgreSQL**
3. Un poller (Spring `@Scheduled` + lock optimiste) lit les entrées non publiées et les envoie à Kafka
4. Une fois publié, `published_at` est mis à jour

### Schéma

```
┌─────────────────────────────────────────────────────────┐
│  trust-credit PostgreSQL                                │
│  ┌──────────────┐         ┌──────────────────┐         │
│  │  credits     │ ◄────── │  outbox_events   │         │
│  │  (status=    │  same   │  (id, aggregate, │         │
│  │   APPROVED)  │  tx     │   topic, payload)│         │
│  └──────────────┘         └────────┬─────────┘         │
│                                    │                    │
└────────────────────────────────────┼────────────────────┘
                                     │ Poller @Scheduled
                                     ▼
                            ┌────────────────┐
                            │  Kafka topic   │
                            │  rnc.credit.   │
                            │  approved      │
                            └────────────────┘
```

## Justification

| Critère | Outbox | XA Transactions | Dual write |
|---------|--------|-----------------|------------|
| Cohérence forte | ✅ | ✅ | ❌ |
| Performance | ✅ Bonne | ❌ Médiocre | ✅ Excellente |
| Simplicité | ⚠️ Moyenne | ❌ Complexe | ✅ Simple |
| Fiabilité | ✅ Élevée | ⚠️ Fragile | ❌ Faible |
| Interop | ✅ REST/Kafka | ⚠️ XA only | ✅ Toutes |

## Implémentation

### Entité `OutboxEvent`

Voir `common-events/src/main/java/bf/rnc/common/events/outbox/OutboxEvent.java`.

### Poller (à implémenter dans chaque microservice)

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepository.findUnpublished(50);
        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload());
                event.setPublishedAt(Instant.now());
                outboxRepository.save(event);
            } catch (Exception e) {
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(e.getMessage());
                outboxRepository.save(event);
                log.warn("Failed to publish outbox event {}: {}", event.getId(), e.getMessage());
            }
        }
    }
}
```

## Conséquences

- **Positives** : cohérence garantie, pas de message fantôme, replay possible, observabilité (table)
- **Négatives** : latence (poller 1s), stockage DB supplémentaire (acceptable), complexité test
- **Latence acceptable** : 1-2s pour les notifications financières (pas temps réel)

## Variantes considérées

- **Debezium CDC** : capture les changements via WAL PostgreSQL → automatique mais nécessite Kafka Connect (stack supplémentaire)
- **Spring ApplicationEventPublisher** : pas fiable en cas de crash

## Références

- https://microservices.io/patterns/data/transactional-outbox.html
- https://debezium.io/
- "Designing Data-Intensive Applications" — Martin Kleppmann, Chapitre 11
