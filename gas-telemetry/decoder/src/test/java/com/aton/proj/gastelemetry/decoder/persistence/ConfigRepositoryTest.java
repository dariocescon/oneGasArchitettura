package com.aton.proj.gastelemetry.decoder.persistence;

import com.aton.proj.gastelemetry.persistence.ConfigEntity;
import com.aton.proj.gastelemetry.persistence.ConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test del repository {@link ConfigRepository} con H2 in-memory.
 *
 * Verifica:
 *   - lookup global (device_id = '*')
 *   - lookup device-specifico
 *   - assenza di valore (Optional.empty)
 *   - aggiornamento di un valore esistente preserva la PK composita
 */
@DataJpaTest
class ConfigRepositoryTest {

    @Autowired
    private ConfigRepository repo;

    @Test
    @DisplayName("findGlobal restituisce la config con device_id = '*'")
    void findGlobal_returnsGlobalConfig() {
        repo.save(new ConfigEntity(ConfigEntity.GLOBAL_DEVICE_ID, "tank.threshold", "20"));

        Optional<ConfigEntity> result = repo.findGlobal("tank.threshold");

        assertThat(result).isPresent();
        assertThat(result.get().getConfigValue()).isEqualTo("20");
        assertThat(result.get().getDeviceId()).isEqualTo("*");
    }

    @Test
    @DisplayName("findForDevice restituisce override device-specifico")
    void findForDevice_returnsDeviceConfig() {
        repo.save(new ConfigEntity(ConfigEntity.GLOBAL_DEVICE_ID, "tank.threshold", "20"));
        repo.save(new ConfigEntity("123456789012345", "tank.threshold", "35"));

        Optional<ConfigEntity> result = repo.findForDevice("123456789012345", "tank.threshold");

        assertThat(result).isPresent();
        assertThat(result.get().getConfigValue()).isEqualTo("35");
    }

    @Test
    @DisplayName("findForDevice ritorna empty se non c'è override device-specifico")
    void findForDevice_emptyWhenAbsent() {
        repo.save(new ConfigEntity(ConfigEntity.GLOBAL_DEVICE_ID, "tank.threshold", "20"));

        Optional<ConfigEntity> result = repo.findForDevice("999999999999999", "tank.threshold");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findGlobal ritorna empty per chiave inesistente")
    void findGlobal_emptyForUnknownKey() {
        assertThat(repo.findGlobal("not.there")).isEmpty();
    }

    @Test
    @DisplayName("save aggiorna il valore di una chiave esistente (stessa PK)")
    void save_updatesExisting() {
        repo.save(new ConfigEntity("123456789012345", "tank.threshold", "20"));
        repo.saveAndFlush(new ConfigEntity("123456789012345", "tank.threshold", "50"));

        Optional<ConfigEntity> result = repo.findForDevice("123456789012345", "tank.threshold");

        assertThat(result).isPresent();
        assertThat(result.get().getConfigValue()).isEqualTo("50");
        assertThat(repo.count()).isEqualTo(1);
    }
}
