-- =============================================================================
--  Schema device_commands per gas-telemetry-worker (PostgreSQL)
--
--  Riferimento DBA. NON eseguito automaticamente da Spring Boot
--  (spring.sql.init.mode=never). Lo schema deve esistere prima dell'avvio
--  del worker (hibernate.ddl-auto=validate verifica solo la corrispondenza
--  con CommandEntity).
--
--  Il DB è condiviso col decoder (PostgreSQL + estensione TimescaleDB).
--  Vedi decoder/src/main/resources/schema-postgresql.sql per device_config
--  e (in step 4) la hypertable delle misure.
-- =============================================================================

CREATE TABLE device_commands (
    id              BIGSERIAL                    PRIMARY KEY,
    device_id       VARCHAR(50)                  NOT NULL,
    device_type     VARCHAR(50)                  NOT NULL,
    command_type    VARCHAR(50)                  NOT NULL,
    command_params  TEXT                         NULL,           -- JSON serializzato di Map<String,Object>
    status          VARCHAR(20)                  NOT NULL  DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ                  NOT NULL  DEFAULT now(),
    sent_at         TIMESTAMPTZ                  NULL,
    updated_at      TIMESTAMPTZ                  NOT NULL  DEFAULT now(),
    error_message   TEXT                         NULL,
    retry_count     INTEGER                      NOT NULL  DEFAULT 0,
    max_retries     INTEGER                      NOT NULL  DEFAULT 3,
    CONSTRAINT ck_device_commands_status CHECK (status IN ('PENDING','IN_PROGRESS','SENT','FAILED'))
);

-- Query principale: claim PENDING + lookup IN_PROGRESS scoped per device.
CREATE INDEX ix_device_commands_device_status ON device_commands(device_id, status);

-- Per ordinamenti cronologici e job di cleanup futuri (zombie IN_PROGRESS).
CREATE INDEX ix_device_commands_created_at ON device_commands(created_at);
