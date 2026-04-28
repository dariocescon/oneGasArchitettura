package com.aton.proj.gastelemetry.decoder.alarm;

import com.aton.proj.gastelemetry.common.Alarm;
import com.aton.proj.gastelemetry.common.DecoderContext;
import com.aton.proj.gastelemetry.persistence.AlarmEntity;
import com.aton.proj.gastelemetry.persistence.AlarmRepository;
import com.aton.proj.gastelemetry.persistence.MeasureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test del {@link MissedTransmissionDetector} con repository e DecoderContext mockati.
 *
 * Verifica:
 *  - emette allarme se elapsed > soglia (default 24h)
 *  - non emette se elapsed < soglia
 *  - usa soglia custom da config
 *  - non duplica l'allarme se già presente dopo lastTimestamp (anti-flood)
 */
class MissedTransmissionDetectorTest {

    private MeasureRepository measureRepo;
    private AlarmRepository   alarmRepo;
    private DecoderContext    ctx;
    private MissedTransmissionDetector detector;

    @BeforeEach
    void setUp() {
        measureRepo = mock(MeasureRepository.class);
        alarmRepo   = mock(AlarmRepository.class);
        ctx         = mock(DecoderContext.class);
        when(alarmRepo.findByDeviceIdAndTimestampBetweenOrderByTimestampAsc(
                anyString(), any(), any())).thenReturn(List.of());
        detector = new MissedTransmissionDetector(measureRepo, alarmRepo, ctx);
    }

    @Test
    @DisplayName("Device fermo da 36 ore (> 24h default) → allarme emesso")
    void emitsAlarm_whenElapsedExceedsThreshold() {
        Instant tooOld = Instant.now().minusSeconds(36 * 3600);
        when(measureRepo.findLastTimestampPerDevice())
                .thenReturn(List.<Object[]>of(new Object[]{"DEV1", tooOld}));

        detector.check();

        verify(alarmRepo).save(any(AlarmEntity.class));
    }

    @Test
    @DisplayName("Device che ha trasmesso 1 ora fa → nessun allarme")
    void noAlarm_withinThreshold() {
        Instant recent = Instant.now().minusSeconds(3600);
        when(measureRepo.findLastTimestampPerDevice())
                .thenReturn(List.<Object[]>of(new Object[]{"DEV1", recent}));

        detector.check();

        verify(alarmRepo, never()).save(any(AlarmEntity.class));
    }

    @Test
    @DisplayName("Soglia custom 10 minuti via config: device fermo da 1h → allarme")
    void customThreshold() {
        Instant elapsedHour = Instant.now().minusSeconds(3600);
        when(measureRepo.findLastTimestampPerDevice())
                .thenReturn(List.<Object[]>of(new Object[]{"DEV_FAST", elapsedHour}));
        when(ctx.<Object>getConfig("DEV_FAST", AlarmCodes.CFG_MISSED_TX_SECONDS)).thenReturn(600);

        detector.check();

        verify(alarmRepo).save(any(AlarmEntity.class));
    }

    @Test
    @DisplayName("Anti-flood: non emette se MISSED_TRANSMISSION già presente dopo lastTimestamp")
    void antiFlood_noDuplicate() {
        Instant tooOld = Instant.now().minusSeconds(36 * 3600);
        when(measureRepo.findLastTimestampPerDevice())
                .thenReturn(List.<Object[]>of(new Object[]{"DEV1", tooOld}));

        // Allarme già presente
        AlarmEntity existing = new AlarmEntity("DEV1",
                new Alarm(tooOld.plusSeconds(3600),
                        AlarmCodes.ALARM_MISSED_TRANSMISSION, "previously emitted"));
        when(alarmRepo.findByDeviceIdAndTimestampBetweenOrderByTimestampAsc(
                anyString(), any(), any())).thenReturn(List.of(existing));

        detector.check();

        verify(alarmRepo, never()).save(any(AlarmEntity.class));
    }

    @Test
    @DisplayName("Nessun device noto → no-op (nessuna chiamata al save)")
    void noKnownDevices() {
        when(measureRepo.findLastTimestampPerDevice()).thenReturn(List.of());

        detector.check();

        verify(alarmRepo, never()).save(any(AlarmEntity.class));
    }
}
