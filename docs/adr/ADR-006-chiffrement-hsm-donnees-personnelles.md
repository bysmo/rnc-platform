# ADR-006 : Chiffrement HSM et protection des données personnelles

- **Statut** : Accepté
- **Date** : 2025-06-30
- **Décideurs** : RSSI RNC, Équipe Architecture, DPO

## Contexte

Le RNC manipule des données personnelles sensibles (CIN, téléphone, adresse, situation financière) de centaines de milliers de citoyens burkinabè. La Loi 010-2004/AN et les directives BCEAO imposent des obligations strictes de protection.

## Décision

Stratégie de chiffrement multi-niveaux :

### 1. Chiffrement au repos (column-level)

Les données personnelles identifiées sont chiffrées **au niveau applicatif** avec AES-256-GCM **avant** écriture en base. La clé maître est stockée dans un **HSM** (Hardware Security Module) en production.

| Champ | Table | Service |
|-------|-------|---------|
| `phone_number_encrypted` | `trust_id.citizens` | trust-id |
| `email_encrypted` | `trust_id.citizens` | trust-id |
| `first_name_encrypted`, `last_name_encrypted` | `trust_id.citizens` | trust-id |
| `address_encrypted` | `trust_id.citizens` | trust-id |
| `cin_number_encrypted` | `trust_id.citizens` | trust-id |
| `document_number_encrypted` | `trust_id.kyc_documents` | trust-id |

### 2. Recherche sur données chiffrées

Pour permettre la recherche (ex: trouver un citoyen par numéro de téléphone), on stocke également un **hash** (SHA-256 avec pepper secret) du champ :
- `phone_number_hash` : permet `WHERE phone_number_hash = ?`
- Hash **non réversible** → ne permet que la recherche exacte, pas le LIKE

### 3. Chiffrement en transit

- TLS 1.3 obligatoire partout (inter-service, externe)
- mTLS entre microservices via service mesh (futur : Istio)
- Certificats Let's Encrypt en production

### 4. Chiffrement au repos PostgreSQL

En complément, **LUKS** sur les volumes disques PostgreSQL (defense in depth).

### 5. HSM en production

- HSM physique (Thales Luna ou Utimaco) ou HSM cloud (AWS CloudHSM)
- Interface PKCS#11
- Clé maître **jamais** en clair en RAM
- Rotation des clés DEK (Data Encryption Keys) tous les 90 jours

## Justification

| Approche | Sécurité | Performance | Complexité | Conformité Loi 010-2004/AN |
|----------|----------|-------------|------------|----------------------------|
| Chiffrement DB natif (pgcrypto) | ⚠️ Moyenne | ✅ Bonne | ✅ Simple | ⚠️ Limite (clé en DB) |
| Chiffrement applicatif sans HSM | ✅ Bonne | ✅ Bonne | ⚠️ Moyenne | ✅ Oui (clé dans config) |
| **Chiffrement applicatif + HSM** | ✅ Excellente | ✅ Bonne | ❌ Élevée | ✅ Oui (conforme BCEAO) |
| Volume LUKS uniquement | ⚠️ Faible | ✅ Excellente | ✅ Simple | ❌ Insuffisant |

## Implémentation

Voir `common-security/src/main/java/bf/rnc/common/security/encryption/HsmEncryptionService.java`.

En développement : clé logicielle (config `rnc.encryption.key`).
En production : délégation HSM via PKCS#11 (`rnc.encryption.hsm.enabled=true`).

## Conséquences

- **Positives** : conformité réglementaire, défense en profondeur, confiance citoyens
- **Négatives** : complexité ops (gestion HSM), impossibilité de LIKE sur données chiffrées (mitigation: search index séparé avec tokens)
- **Coût HSM** : ~30M FCFA (CAPEX) + maintenance annuelle

## Risques et mitigations

| Risque | Mitigation |
|--------|------------|
| Perte clé maître HSM | Backup HSM + quorum M-of-N pour restauration |
| Compromission HSM | Détection intrusion, audit log, alerte temps réel |
| Performance dégradation | Tests de charge, cache clés DEK en mémoire sécurisée |
| Bug applicatif | Tests unitaires exhaustifs, code review obligatoire |

## Références

- Loi 010-2004/AN du 20 avril 2004 sur la protection des données personnelles au Burkina Faso
- BCEAO Directive DI-2019 sur la sécurité des services de paiement
- NIST SP 800-57 : Recommendation for Key Management
- FIPS 140-3 : Security Requirements for Cryptographic Modules
