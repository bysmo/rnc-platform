# ADR-003 : Authentification Keycloak (OIDC/OAuth2) + JWT

- **Statut** : Accepté
- **Date** : 2025-06-30
- **Décideurs** : Équipe Architecture RNC, RSSI

## Contexte

Le RNC est une infrastructure nationale qui sera utilisée par plusieurs centaines de milliers de citoyens et des milliers de marchands. L'authentification doit supporter :
- Multi-acteurs (citoyens, marchands, agents, admins, auditeurs)
- MFA (OTP SMS) pour opérations sensibles
- RBAC strict avec audit
- Fédération future avec état-civil Burkina Faso
- SSO avec portails partenaires (banques, assurances)
- Conformité Loi 010-2004/AN et exigences BCEAO

## Décision

Adoption de **Keycloak 25** comme serveur d'identité, en mode OIDC/OAuth2.

### Architecture IAM

```
┌─────────────────┐         ┌──────────────┐         ┌─────────────┐
│ Mobile Flutter  │ ──────► │ API Gateway  │ ──────► │ Microservice│
└────────┬────────┘         │  (validates  │         │ (resource   │
         │                  │   JWT)       │         │  server)    │
         │                  └──────┬───────┘         └─────────────┘
         │                         │
         │      Authorization Code │ /realms/rnc/.well-known/openid-configuration
         ▼                         ▼
┌──────────────────────────────────────────────────┐
│                 Keycloak Realm "rnc"             │
│  ├─ Clients: rnc-mobile-app, rnc-admin-portal    │
│  ├─ Roles: CITIZEN, MERCHANT, ADMIN, AUDITOR...  │
│  ├─ Groups: citizens, merchants, agents, admins  │
│  ├─ Flows: MFA OTP SMS                           │
│  └─ Federated storage: PostgreSQL                │
└──────────────────────────────────────────────────┘
```

## Justification

| Critère | Keycloak | Spring Auth Server | Auth0 | Cognito |
|---------|----------|-------------------|-------|---------|
| Open source / self-hosted | ✅ Oui | ✅ Oui | ❌ Non | ❌ Non |
| Coût | ✅ Aucun | ✅ Aucun | ❌ Par utilisateur | ❌ Par utilisateur |
| Données au Burkina Faso | ✅ Oui | ✅ Oui | ❌ Non | ❌ Non |
| MFA SMS natif | ⚠️ Via SPI | ❌ À coder | ✅ Oui | ✅ Oui |
| Fédération LDAP/AD | ✅ Oui | ⚠️ Limité | ✅ Oui | ✅ Oui |
| RBAC + groupes | ✅ Oui | ⚠️ Limité | ✅ Oui | ✅ Oui |
| Conformité souveraineté | ✅ Oui | ✅ Oui | ❌ Non | ❌ Non |

### Argument décisif : souveraineté nationale

Le RNC est une infrastructure d'État. Les données d'identité des citoyens burkinabè **doivent** être hébergées sur le territoire national (Loi 010-2004/AN). Keycloak auto-hébergé respecte cette contrainte.

## Conséquences

- **Positives** : souveraineté, MFA via SPI custom, SSO prêt, audit centralisé, conformité réglementaire.
- **Négatives** : un service supplémentaire à opérer, configuration initiale complexe, montée en charge à anticiper (cluster Keycloak).
- **Coût** : 2 instances Keycloak en production (HA), 1 en preprod, 1 en dev.

## Risques et mitigations

| Risque | Mitigation |
|--------|------------|
| Keycloak = SPOF | Cluster multi-instance + sticky sessions |
| OTP SMS = dépendance opérateur | Multi-opérateurs (Orange, Moov, Telecel) + fallback |
| Volume utilisateurs (100k+) | Tests de charge, cache Redis pour tokens |
| Compromission realm | Rotation secrets, MFA admin, audit continu |

## Implémentation

- Realm `rnc` importé au démarrage via JSON (`docker/keycloak/rnc-realm.json`)
- 3 clients OIDC : mobile (public), admin-portal (confidentiel), gateway (service account)
- 11 rôles métier mappés aux responsabilités RNC
- SPI custom à développer : `OtpSmsAuthenticator` (intégration passerelle SMS burkinabè)

## Références

- https://www.keycloak.org/documentation
- BCEAO Directive DI-2019 sur les services de paiement
- Loi 010-2004/AN du 20 avril 2004 sur la protection des données personnelles au Burkina Faso
