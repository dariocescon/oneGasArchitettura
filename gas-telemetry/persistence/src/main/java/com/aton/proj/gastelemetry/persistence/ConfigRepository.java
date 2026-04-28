package com.aton.proj.gastelemetry.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository per {@link ConfigEntity}.
 *
 * <p>Lookup tipici:
 * <ul>
 *   <li>{@link #findGlobal(String)} — config "default" (deviceId = {@link ConfigEntity#GLOBAL_DEVICE_ID})</li>
 *   <li>{@link #findForDevice(String, String)} — override device-specifico</li>
 * </ul>
 *
 * <p>La logica di fallback (device → global) è in {@code DecoderContextImpl}, non qui.
 */
@Repository
public interface ConfigRepository
        extends JpaRepository<ConfigEntity, ConfigEntity.ConfigKey> {

    default Optional<ConfigEntity> findGlobal(String configKey) {
        return findById(new ConfigEntity.ConfigKey(ConfigEntity.GLOBAL_DEVICE_ID, configKey));
    }

    default Optional<ConfigEntity> findForDevice(String deviceId, String configKey) {
        return findById(new ConfigEntity.ConfigKey(deviceId, configKey));
    }
}
