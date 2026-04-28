package com.aton.proj.gastelemetry.worker.persistence;

import com.aton.proj.gastelemetry.persistence.CommandEntity;
import com.aton.proj.gastelemetry.persistence.CommandRepository;
import com.aton.proj.gastelemetry.persistence.CommandStatus;
import com.aton.proj.gastelemetry.worker.impl.Tek822Encoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test del CommandService end-to-end con repository reale (H2) + encoder reale.
 *
 * Verifica:
 * <ul>
 *   <li>{@code claimAndEncode} produce stringhe ASCII, con REBOOT auto-appended se ci sono S-commands</li>
 *   <li>Round-trip JSON dei {@code commandParams}</li>
 *   <li>{@code markSent} su lista vuota è no-op</li>
 *   <li>Flusso completo: claim → mark → seconda claim restituisce vuoto</li>
 * </ul>
 *
 * Usa {@code @DataJpaTest} + {@code @Import(CommandService.class)} per slicing minimale:
 * solo JPA + ObjectMapper (auto-config) + il service sotto test.
 */
@DataJpaTest
@AutoConfigureTestDatabase
@Import({ CommandService.class, CommandServiceTest.TestConfig.class })
class CommandServiceTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }


    private static final String DEVICE_A = "111111111111111";

    @Autowired
    private CommandService service;

    @Autowired
    private CommandRepository repo;

    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("claimAndEncode con S-command produce ASCII con REBOOT auto-appended")
    void claimAndEncode_appendsRebootForSCommand() {
        // SET_INTERVAL (S0) richiede l'auto-append di REBOOT (R3=ACTIVE)
        Map<String, Object> params = new HashMap<>();
        params.put("interval", 4);
        params.put("samplingPeriod", 1);
        saveCommand(DEVICE_A, Tek822Encoder.CMD_SET_INTERVAL, params);

        CommandService.ClaimedBatch batch = service.claimAndEncode(DEVICE_A);

        assertThat(batch.isEmpty()).isFalse();
        assertThat(batch.ids()).hasSize(1);              // 1 riga in DB
        assertThat(batch.encoded()).hasSize(2);          // 1 SET_INTERVAL + 1 REBOOT sintetico

        // Stringhe ASCII direttamente:
        //  - 1° = "TEK822,S0=..."  (SET_INTERVAL con interval=4 hours, sampling=1 → S0=128+16 = 144 = 0x90)
        //  - 2° = "TEK822,R3=ACTIVE"
        assertThat(batch.encoded()[0]).startsWith("TEK822,S0=");
        assertThat(batch.encoded()[1]).isEqualTo("TEK822,R3=ACTIVE");

        // DB: 1 riga in IN_PROGRESS (il REBOOT sintetico NON è persistito)
        assertThat(repo.findByDeviceIdAndStatusOrderByCreatedAtAsc(DEVICE_A, CommandStatus.IN_PROGRESS))
                .hasSize(1);
    }

    @Test
    @DisplayName("claimAndEncode senza comandi PENDING ritorna batch vuoto")
    void claimAndEncode_noPending_returnsEmpty() {
        CommandService.ClaimedBatch batch = service.claimAndEncode(DEVICE_A);

        assertThat(batch.isEmpty()).isTrue();
        assertThat(batch.ids()).isEmpty();
        assertThat(batch.encoded()).isEmpty();
    }

    @Test
    @DisplayName("Round-trip: JSON commandParams → Map → encode")
    void claimAndEncode_jsonRoundTrip() {
        Map<String, Object> params = new HashMap<>();
        params.put("apn", "stream.co.uk");
        params.put("username", "streamip");
        params.put("apnPassword", "streamip");
        saveCommand(DEVICE_A, Tek822Encoder.CMD_SET_APN, params);

        CommandService.ClaimedBatch batch = service.claimAndEncode(DEVICE_A);

        // Anche SET_APN è S-command → batch.encoded ha SET_APN + REBOOT
        assertThat(batch.encoded()).hasSize(2);
        String first = batch.encoded()[0];
        assertThat(first).contains("S12=stream.co.uk");
        assertThat(first).contains("S13=streamip");
        assertThat(first).contains("S14=streamip");
    }

    @Test
    @DisplayName("markSent su lista vuota è no-op")
    void markSent_emptyList_noOp() {
        // Non deve sollevare eccezioni
        service.markSent(List.of());
        service.markSent(null);
    }

    @Test
    @DisplayName("Flusso completo: claim → mark → seconda claim vuota")
    void fullFlow() {
        Map<String, Object> params = Map.of();
        saveCommand(DEVICE_A, Tek822Encoder.CMD_REQUEST_STATUS, params);

        CommandService.ClaimedBatch batch = service.claimAndEncode(DEVICE_A);
        assertThat(batch.encoded()).hasSize(1); // REQUEST_STATUS non è S-command, no REBOOT
        assertThat(batch.encoded()[0]).isEqualTo("TEK822,R6=02");

        service.markSent(batch.ids());

        // DB: nessun IN_PROGRESS, 1 SENT
        assertThat(repo.findByDeviceIdAndStatusOrderByCreatedAtAsc(DEVICE_A, CommandStatus.IN_PROGRESS))
                .isEmpty();
        assertThat(repo.findByDeviceIdAndStatusOrderByCreatedAtAsc(DEVICE_A, CommandStatus.SENT))
                .hasSize(1)
                .allSatisfy(e -> assertThat(e.getSentAt()).isNotNull());

        // Seconda claim → batch vuoto
        assertThat(service.claimAndEncode(DEVICE_A).isEmpty()).isTrue();
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private void saveCommand(String deviceId, String commandType, Map<String, Object> params) {
        CommandEntity e = new CommandEntity();
        e.setDeviceId(deviceId);
        e.setDeviceType("TEK822V1");
        e.setCommandType(commandType);
        try {
            e.setCommandParams(mapper.writeValueAsString(params));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot serialize params", ex);
        }
        e.setStatus(CommandStatus.PENDING);
        repo.saveAndFlush(e);
    }

}
