-- ============================================================
-- trust-school — Schéma initial
-- ============================================================
-- TODO: créer les tables spécifiques à ce microservice
-- Conventions RNC:
--   * toute table a: id (UUID), created_at, updated_at, created_by, updated_by, deleted, version
--   * utilisation de jsonb pour les données flexibles
--   * index systématiques sur les clés étrangères et les colonnes filtrées
-- ============================================================

CREATE SCHEMA IF NOT EXISTS trust_school;

-- Exemple:
-- CREATE TABLE trust_school.entity_example (
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
-- CREATE INDEX idx_entity_example_status ON trust_school.entity_example(status) WHERE NOT deleted;
-- CREATE INDEX idx_entity_example_created_at ON trust_school.entity_example(created_at);
