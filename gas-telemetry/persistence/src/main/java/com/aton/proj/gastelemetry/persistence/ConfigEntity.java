package com.aton.proj.gastelemetry.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Configurazione chiave/valore associata a un device (o globale).
 *
 * <p>Schema key-value: massima flessibilità senza dover migrare il DDL ad ogni
 * nuova chiave (es. soglie allarmi, timeout custom, abilitazione feature).
 * Il valore è sempre {@code String}; il consumatore (es. {@code DecoderContextImpl})
 * fa il parsing tipato.
 *
 * <p><b>Convenzione device_id:</b> per le configurazioni <i>globali</i>
 * (default applicati a tutti i device) si usa il sentinel {@link #GLOBAL_DEVICE_ID}
 * = {@code "*"}. Non si usa NULL perché complica la PK composita.
 *
 * <p>Tabella: {@code device_config}, PK composita {@code (device_id, config_key)}.
 */
@Entity
@Table(name = "device_config")
@IdClass(ConfigEntity.ConfigKey.class)
public class ConfigEntity {

    /** Sentinel usato per le configurazioni globali (non specifiche di un device). */
    public static final String GLOBAL_DEVICE_ID = "*";

    @Id
    @Column(name = "device_id", nullable = false, length = 50)
    private String deviceId;

    @Id
    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ConfigEntity() {}

    public ConfigEntity(String deviceId, String configKey, String configValue) {
        this.deviceId = deviceId;
        this.configKey = configKey;
        this.configValue = configValue;
    }

    @PrePersist
    @PreUpdate
    public void touch() {
        updatedAt = Instant.now();
    }

    public String getDeviceId()              { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getConfigKey()             { return configKey; }
    public void setConfigKey(String key)     { this.configKey = key; }

    public String getConfigValue()           { return configValue; }
    public void setConfigValue(String val)   { this.configValue = val; }

    public Instant getUpdatedAt()            { return updatedAt; }
    public void setUpdatedAt(Instant ts)    { this.updatedAt = ts; }

    /** PK composita richiesta da {@link IdClass}. */
    public static class ConfigKey implements Serializable {
        private String deviceId;
        private String configKey;

        public ConfigKey() {}
        public ConfigKey(String deviceId, String configKey) {
            this.deviceId = deviceId;
            this.configKey = configKey;
        }

        public String getDeviceId()  { return deviceId; }
        public String getConfigKey() { return configKey; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ConfigKey k)) return false;
            return Objects.equals(deviceId, k.deviceId)
                    && Objects.equals(configKey, k.configKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(deviceId, configKey);
        }
    }
}
