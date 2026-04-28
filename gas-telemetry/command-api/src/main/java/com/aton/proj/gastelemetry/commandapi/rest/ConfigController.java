package com.aton.proj.gastelemetry.commandapi.rest;

import com.aton.proj.gastelemetry.persistence.ConfigEntity;
import com.aton.proj.gastelemetry.persistence.ConfigRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestione della tabella {@code device_config}.
 *
 * <p>Per il device id "globale" si usa il sentinel {@code *} (URL-encode {@code %2A});
 * vedi {@link ConfigEntity#GLOBAL_DEVICE_ID}.
 *
 * <ul>
 *   <li>{@code GET /api/config/{deviceId}/{key}} — leggi (no fallback automatico)</li>
 *   <li>{@code PUT /api/config/{deviceId}/{key}} — upsert (body = string del valore)</li>
 *   <li>{@code DELETE /api/config/{deviceId}/{key}} — rimuovi</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigRepository repo;

    public ConfigController(ConfigRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/{deviceId}/{key}")
    public ResponseEntity<ConfigEntity> get(@PathVariable String deviceId,
                                            @PathVariable String key) {
        return repo.findForDevice(deviceId, key)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{deviceId}/{key}")
    public ConfigEntity upsert(@PathVariable String deviceId,
                               @PathVariable String key,
                               @RequestBody String value) {
        ConfigEntity entity = repo.findForDevice(deviceId, key)
                .orElse(new ConfigEntity(deviceId, key, value));
        entity.setConfigValue(value);
        return repo.save(entity);
    }

    @DeleteMapping("/{deviceId}/{key}")
    public ResponseEntity<Void> delete(@PathVariable String deviceId,
                                       @PathVariable String key) {
        if (repo.findForDevice(deviceId, key).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        repo.deleteById(new ConfigEntity.ConfigKey(deviceId, key));
        return ResponseEntity.noContent().build();
    }
}
