package com.aton.proj.gastelemetry.decoder.persistence;

import com.aton.proj.gastelemetry.common.Alarm;
import com.aton.proj.gastelemetry.common.DecodedPacket;
import com.aton.proj.gastelemetry.common.Measure;
import com.aton.proj.gastelemetry.persistence.AlarmEntity;
import com.aton.proj.gastelemetry.persistence.AlarmRepository;
import com.aton.proj.gastelemetry.persistence.MeasureEntity;
import com.aton.proj.gastelemetry.persistence.MeasureRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test del {@link TimeSeriesService} con repository reali su H2.
 *
 * Verifica:
 *   - persist su entrambe le tabelle in un'unica chiamata
 *   - pacchetto vuoto è no-op
 *   - solo misure / solo allarmi
 *   - lookup time-window via repository
 */
@DataJpaTest
@Import(TimeSeriesService.class)
class TimeSeriesServiceTest {

    @Autowired private TimeSeriesService service;
    @Autowired private MeasureRepository measureRepo;
    @Autowired private AlarmRepository   alarmRepo;

    private static final Instant T0 = Instant.parse("2026-04-28T10:00:00Z");

    @Test
    @DisplayName("persist scrive misure e allarmi in un'unica transazione")
    void persist_writesBoth() {
        DecodedPacket packet = new DecodedPacket(
                List.of(
                        new Measure(T0, "1-0:1.8.0", 1234.5, "m3"),
                        new Measure(T0.plusSeconds(60), "1-0:1.8.0", 1234.6, "m3")
                ),
                List.of(new Alarm(T0, "TANK_LOW", "soglia raggiunta"))
        );

        service.persist("DEV1", packet);

        assertThat(measureRepo.count()).isEqualTo(2);
        assertThat(alarmRepo.count()).isEqualTo(1);
        MeasureEntity m = measureRepo.findAll().get(0);
        assertThat(m.getDeviceId()).isEqualTo("DEV1");
        assertThat(m.getObisCode()).isEqualTo("1-0:1.8.0");
        assertThat(m.getUnit()).isEqualTo("m3");
        AlarmEntity a = alarmRepo.findAll().get(0);
        assertThat(a.getAlarmCode()).isEqualTo("TANK_LOW");
    }

    @Test
    @DisplayName("persist con DecodedPacket vuoto è no-op (no INSERT)")
    void persist_emptyPacket() {
        service.persist("DEV1", new DecodedPacket(List.of(), List.of()));

        assertThat(measureRepo.count()).isZero();
        assertThat(alarmRepo.count()).isZero();
    }

    @Test
    @DisplayName("persist con solo misure non tocca device_alarms")
    void persist_onlyMeasures() {
        DecodedPacket packet = new DecodedPacket(
                List.of(new Measure(T0, "1-0:1.8.0", 100.0, "m3")),
                List.of()
        );
        service.persist("DEV1", packet);

        assertThat(measureRepo.count()).isEqualTo(1);
        assertThat(alarmRepo.count()).isZero();
    }

    @Test
    @DisplayName("findByDeviceIdAndTimestampBetween filtra correttamente per finestra temporale")
    void timeWindowQuery() {
        Measure m1 = new Measure(T0,                       "1-0:1.8.0", 100.0, "m3");
        Measure m2 = new Measure(T0.plusSeconds(3600),     "1-0:1.8.0", 110.0, "m3");
        Measure m3 = new Measure(T0.plusSeconds(2 * 3600), "1-0:1.8.0", 120.0, "m3");
        service.persist("DEV1", new DecodedPacket(List.of(m1, m2, m3), List.of()));

        List<MeasureEntity> window = measureRepo
                .findByDeviceIdAndTimestampBetweenOrderByTimestampAsc(
                        "DEV1", T0.plusSeconds(1800), T0.plusSeconds(5000));

        assertThat(window).hasSize(1);
        assertThat(window.get(0).getValue()).isEqualTo(110.0);
    }

    @Test
    @DisplayName("persist scopa per deviceId — ricerca su altro device non vede dati")
    void persist_scopedByDevice() {
        service.persist("DEV1", new DecodedPacket(
                List.of(new Measure(T0, "1-0:1.8.0", 100.0, "m3")), List.of()));

        List<MeasureEntity> other = measureRepo
                .findByDeviceIdAndTimestampBetweenOrderByTimestampAsc(
                        "DEV2", T0.minusSeconds(1), T0.plusSeconds(1));

        assertThat(other).isEmpty();
    }
}
