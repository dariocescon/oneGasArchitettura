-- =============================================================================
--  Schema decoder per gas-telemetry-decoder (PostgreSQL + TimescaleDB)
--
--  Riferimento DBA. NON eseguito automaticamente da Spring Boot
--  (spring.sql.init.mode=never). Lo schema deve esistere prima dell'avvio
--  (hibernate.ddl-auto=validate).
--
--  Il DB è condiviso col worker (vedi worker/src/main/resources/schema-postgresql.sql
--  per device_commands).
-- =============================================================================

-- Estensione richiesta: TimescaleDB.
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- -----------------------------------------------------------------------------
--  device_config: configurazioni key-value, per device o globali (sentinel '*').
--
--  Casi d'uso:
--    - Soglie allarmi (es. tank.threshold)
--    - Timeout / intervalli operativi
--    - Feature flag per device specifici
--
--  Risoluzione a runtime in DecoderContextImpl: prima si cerca (device_id, key),
--  poi fallback su ('*', key).
-- -----------------------------------------------------------------------------
CREATE TABLE device_config (
    device_id    VARCHAR(50)    NOT NULL,            -- '*' per config globale
    config_key   VARCHAR(100)   NOT NULL,
    config_value TEXT           NULL,
    updated_at   TIMESTAMPTZ    NOT NULL  DEFAULT now(),
    PRIMARY KEY (device_id, config_key)
);

-- -----------------------------------------------------------------------------
--  device_measures: misure decodificate (hypertable TimescaleDB).
--
--  Partition column: timestamp. Chunk di 7 giorni (parametro tipico per
--  device che inviano qualche pacchetto/giorno; aggiustare a seconda
--  della cardinalità).
--
--  Indice (device_id, timestamp DESC) — la query principale è
--  "ultime N misure di un device" o "misure di un device in finestra temporale".
-- -----------------------------------------------------------------------------
CREATE TABLE device_measures (
    id          BIGSERIAL,
    device_id   VARCHAR(50)        NOT NULL,
    timestamp   TIMESTAMPTZ        NOT NULL,
    obis_code     VARCHAR(50)        NOT NULL,
    measure_value DOUBLE PRECISION   NOT NULL,         -- "value" è reserved in molti dialetti
    unit          VARCHAR(20)        NULL,
    PRIMARY KEY (id, timestamp)    -- la PK delle hypertable deve includere la partition column
);

SELECT create_hypertable('device_measures', 'timestamp',
                         chunk_time_interval => INTERVAL '7 days');

CREATE INDEX ix_device_measures_device_ts
    ON device_measures (device_id, timestamp DESC);

-- -----------------------------------------------------------------------------
--  device_alarms: allarmi (hypertable TimescaleDB), stesso schema temporale.
-- -----------------------------------------------------------------------------
CREATE TABLE device_alarms (
    id          BIGSERIAL,
    device_id   VARCHAR(50)        NOT NULL,
    timestamp   TIMESTAMPTZ        NOT NULL,
    alarm_code  VARCHAR(50)        NOT NULL,
    description TEXT               NULL,
    PRIMARY KEY (id, timestamp)
);

SELECT create_hypertable('device_alarms', 'timestamp',
                         chunk_time_interval => INTERVAL '7 days');

CREATE INDEX ix_device_alarms_device_ts
    ON device_alarms (device_id, timestamp DESC);
