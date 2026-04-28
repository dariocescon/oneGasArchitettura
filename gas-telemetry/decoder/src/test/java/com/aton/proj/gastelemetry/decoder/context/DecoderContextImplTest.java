package com.aton.proj.gastelemetry.decoder.context;

import com.aton.proj.gastelemetry.common.Alarm;
import com.aton.proj.gastelemetry.common.DecodedPacket;
import com.aton.proj.gastelemetry.common.Measure;
import com.aton.proj.gastelemetry.decoder.persistence.TimeSeriesService;
import com.aton.proj.gastelemetry.persistence.ConfigEntity;
import com.aton.proj.gastelemetry.persistence.ConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test di {@link DecoderContextImpl#getConfig} con repository mockato.
 *
 * Verifica:
 *   - parsing tipato (int, double, boolean, string)
 *   - fallback device → global
 *   - lookup global puro
 *   - null se la chiave non esiste in nessun layer
 */
class DecoderContextImplTest {

    private ConfigRepository repo;
    private TimeSeriesService timeSeriesService;
    private DecoderContextImpl ctx;

    @BeforeEach
    void setUp() {
        repo = mock(ConfigRepository.class);
        timeSeriesService = mock(TimeSeriesService.class);
        ctx  = new DecoderContextImpl(repo, timeSeriesService, List.of());
    }

    @Test
    @DisplayName("publishDecodedData delega a TimeSeriesService.persist")
    void publishDecodedData_delegatesToTimeSeriesService() {
        DecodedPacket packet = new DecodedPacket(
                List.of(new Measure(Instant.parse("2026-04-28T10:00:00Z"), "1-0:1.8.0", 1234.5, "m3")),
                List.of(new Alarm(Instant.parse("2026-04-28T10:00:00Z"), "TANK_LOW", "soglia"))
        );

        ctx.publishDecodedData("DEV1", packet);

        // Senza regole iniettate, l'enriched packet è identico all'originale
        verify(timeSeriesService).persist("DEV1", packet);
    }

    @Test
    @DisplayName("publishDecodedData applica le AlarmRule e accoda gli allarmi prodotti")
    void publishDecodedData_appliesRules() {
        // AlarmRule che produce sempre 1 allarme sintetico
        com.aton.proj.gastelemetry.decoder.alarm.AlarmRule alwaysFires =
                (devId, m, c) -> List.of(new Alarm(Instant.parse("2026-04-28T10:00:00Z"),
                        "SYNTH", "rule fired"));
        DecoderContextImpl ctxWithRule = new DecoderContextImpl(
                repo, timeSeriesService, List.of(alwaysFires));

        DecodedPacket packet = new DecodedPacket(
                List.of(new Measure(Instant.parse("2026-04-28T10:00:00Z"), "X", 1.0, "u")),
                List.of()
        );

        ctxWithRule.publishDecodedData("DEV1", packet);

        org.mockito.ArgumentCaptor<DecodedPacket> captor =
                org.mockito.ArgumentCaptor.forClass(DecodedPacket.class);
        verify(timeSeriesService).persist(org.mockito.ArgumentMatchers.eq("DEV1"), captor.capture());
        assertThat(captor.getValue().alarms()).hasSize(1);
        assertThat(captor.getValue().alarms().get(0).alarmCode()).isEqualTo("SYNTH");
    }

    @Test
    @DisplayName("getConfig(key) parsa Integer da global config")
    void getConfig_parsesInteger() {
        when(repo.findGlobal("tank.threshold")).thenReturn(
                Optional.of(new ConfigEntity("*", "tank.threshold", "20")));

        Integer value = ctx.getConfig("tank.threshold");
        assertThat(value).isEqualTo(20);
    }

    @Test
    @DisplayName("getConfig(key) parsa Double per valori non interi")
    void getConfig_parsesDouble() {
        when(repo.findGlobal("temp.max")).thenReturn(
                Optional.of(new ConfigEntity("*", "temp.max", "12.5")));

        Double value = ctx.getConfig("temp.max");
        assertThat(value).isEqualTo(12.5);
    }

    @Test
    @DisplayName("getConfig(key) parsa Boolean")
    void getConfig_parsesBoolean() {
        when(repo.findGlobal("alarms.enabled")).thenReturn(
                Optional.of(new ConfigEntity("*", "alarms.enabled", "true")));

        Boolean value = ctx.getConfig("alarms.enabled");
        assertThat(value).isTrue();
    }

    @Test
    @DisplayName("getConfig(key) ritorna String se non parsabile come numero/booleano")
    void getConfig_fallbackString() {
        when(repo.findGlobal("apn.name")).thenReturn(
                Optional.of(new ConfigEntity("*", "apn.name", "iot.example.com")));

        String value = ctx.getConfig("apn.name");
        assertThat(value).isEqualTo("iot.example.com");
    }

    @Test
    @DisplayName("getConfig(key) ritorna null se la chiave non esiste")
    void getConfig_missingKey() {
        when(repo.findGlobal("not.there")).thenReturn(Optional.empty());
        assertThat(ctx.<Object>getConfig("not.there")).isNull();
    }

    @Test
    @DisplayName("getConfig(deviceId, key) usa override device-specifico se presente")
    void getConfig_deviceOverride() {
        when(repo.findForDevice("DEV1", "tank.threshold")).thenReturn(
                Optional.of(new ConfigEntity("DEV1", "tank.threshold", "35")));

        Integer value = ctx.getConfig("DEV1", "tank.threshold");
        assertThat(value).isEqualTo(35);
    }

    @Test
    @DisplayName("getConfig(deviceId, key) fallback su global se device non ha override")
    void getConfig_fallbackToGlobal() {
        when(repo.findForDevice("DEV1", "tank.threshold")).thenReturn(Optional.empty());
        when(repo.findGlobal("tank.threshold")).thenReturn(
                Optional.of(new ConfigEntity("*", "tank.threshold", "20")));

        Integer value = ctx.getConfig("DEV1", "tank.threshold");
        assertThat(value).isEqualTo(20);
    }

    @Test
    @DisplayName("getConfig(deviceId, key) ritorna null se né device né global hanno la chiave")
    void getConfig_neitherDeviceNorGlobal() {
        when(repo.findForDevice("DEV1", "x")).thenReturn(Optional.empty());
        when(repo.findGlobal("x")).thenReturn(Optional.empty());

        assertThat(ctx.<Object>getConfig("DEV1", "x")).isNull();
    }
}
