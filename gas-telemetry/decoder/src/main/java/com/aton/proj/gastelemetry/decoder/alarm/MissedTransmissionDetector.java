package com.aton.proj.gastelemetry.decoder.alarm;

import com.aton.proj.gastelemetry.common.Alarm;
import com.aton.proj.gastelemetry.common.DecoderContext;
import com.aton.proj.gastelemetry.persistence.AlarmEntity;
import com.aton.proj.gastelemetry.persistence.AlarmRepository;
import com.aton.proj.gastelemetry.persistence.MeasureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Job periodico che rileva i device che non trasmettono da troppo tempo ed emette
 * un allarme {@link AlarmCodes#ALARM_MISSED_TRANSMISSION}.
 *
 * <p>A differenza delle {@link AlarmRule} sincrone, questo detector non guarda
 * <i>quello che arriva</i> ma <i>quello che manca</i>: per ogni device noto
 * (= con almeno una misura passata) confronta {@code now() - lastTimestamp} con
 * la soglia configurata.
 *
 * <p><b>Configurazione:</b>
 * <ul>
 *   <li>Cadenza job: {@code decoder.missed-transmission.check-interval-ms}
 *       (default 5 minuti). Tarata per essere infrequente — l'allarme stesso
 *       è di scala orarie/giornaliere.</li>
 *   <li>Soglia per device: {@link AlarmCodes#CFG_MISSED_TX_SECONDS} via
 *       {@link DecoderContext#getConfig(String, String)} (fallback global).
 *       Default {@link #DEFAULT_THRESHOLD_SECONDS} = 24h.</li>
 * </ul>
 *
 * <p><b>Anti-flood:</b> non riemette l'allarme se un {@code MISSED_TRANSMISSION}
 * dello stesso device è già presente <i>dopo</i> {@code lastTimestamp}. Questo
 * evita di triplicare l'allarme ogni 5 minuti per un device offline.
 *
 * <p>Disattivato in profilo {@code local} per evitare scritture su H2 durante
 * lo sviluppo (e per non sporcare i test che caricano il context).
 */
@Component
@Profile("!local")
public class MissedTransmissionDetector {

    private static final Logger log = LoggerFactory.getLogger(MissedTransmissionDetector.class);

    static final long DEFAULT_THRESHOLD_SECONDS = 24 * 3600; // 24h

    private final MeasureRepository measureRepo;
    private final AlarmRepository   alarmRepo;
    private final DecoderContext    decoderContext;

    public MissedTransmissionDetector(MeasureRepository measureRepo,
                                      AlarmRepository alarmRepo,
                                      DecoderContext decoderContext) {
        this.measureRepo    = measureRepo;
        this.alarmRepo      = alarmRepo;
        this.decoderContext = decoderContext;
    }

    @Scheduled(fixedDelayString = "${decoder.missed-transmission.check-interval-ms:300000}")
    public void check() {
        Instant now = Instant.now();
        List<Object[]> lastByDevice = measureRepo.findLastTimestampPerDevice();
        int emitted = 0;

        for (Object[] row : lastByDevice) {
            String  deviceId      = (String) row[0];
            Instant lastTimestamp = (Instant) row[1];
            if (deviceId == null || lastTimestamp == null) continue;

            long thresholdSeconds = thresholdSecondsFor(deviceId);
            Duration elapsed = Duration.between(lastTimestamp, now);
            if (elapsed.getSeconds() <= thresholdSeconds) continue;

            // Anti-flood: l'allarme è già stato emesso dopo lastTimestamp?
            List<AlarmEntity> recent = alarmRepo
                    .findByDeviceIdAndTimestampBetweenOrderByTimestampAsc(
                            deviceId, lastTimestamp, now);
            boolean alreadyEmitted = recent.stream()
                    .anyMatch(a -> AlarmCodes.ALARM_MISSED_TRANSMISSION.equals(a.getAlarmCode()));
            if (alreadyEmitted) continue;

            Alarm alarm = new Alarm(now, AlarmCodes.ALARM_MISSED_TRANSMISSION,
                    "No data from " + deviceId + " for " + elapsed.getSeconds() + "s "
                            + "(threshold " + thresholdSeconds + "s)");
            alarmRepo.save(new AlarmEntity(deviceId, alarm));
            emitted++;
        }

        if (emitted > 0) {
            log.info("MissedTransmissionDetector: emessi {} allarmi su {} device noti",
                    emitted, lastByDevice.size());
        } else {
            log.debug("MissedTransmissionDetector: nessun allarme emesso ({} device controllati)",
                    lastByDevice.size());
        }
    }

    private long thresholdSecondsFor(String deviceId) {
        Object raw = decoderContext.getConfig(deviceId, AlarmCodes.CFG_MISSED_TX_SECONDS);
        if (raw == null) return DEFAULT_THRESHOLD_SECONDS;
        if (raw instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException e) {
            return DEFAULT_THRESHOLD_SECONDS;
        }
    }
}
