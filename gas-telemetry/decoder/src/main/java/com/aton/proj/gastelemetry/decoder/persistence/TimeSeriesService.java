package com.aton.proj.gastelemetry.decoder.persistence;

import com.aton.proj.gastelemetry.common.DecodedPacket;
import com.aton.proj.gastelemetry.persistence.AlarmEntity;
import com.aton.proj.gastelemetry.persistence.AlarmRepository;
import com.aton.proj.gastelemetry.persistence.MeasureEntity;
import com.aton.proj.gastelemetry.persistence.MeasureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Persiste un {@link DecodedPacket} sulle hypertable {@code device_measures}
 * e {@code device_alarms} con un'unica transazione.
 *
 * <p>Atomicità: misure e allarmi dello stesso pacchetto vivono o falliscono
 * insieme. Eventuali rollback parziali (es. solo le misure committate) sono
 * scartati.
 *
 * <p>Performance: usiamo {@code saveAll} di Spring Data, che con
 * {@code hibernate.jdbc.batch_size=50} e {@code order_inserts=true} (vedi
 * {@code application.properties}) si traduce in batch INSERT JDBC. La
 * configurazione è cruciale per sostenere il rate di Event Hub in produzione.
 *
 * <p>NB: per non innescare il SELECT-prima-di-INSERT su nuove entity, le
 * istanze passate hanno {@code id == null} (default JPA con
 * {@code GenerationType.IDENTITY}).
 */
@Service
public class TimeSeriesService {

    private static final Logger log = LoggerFactory.getLogger(TimeSeriesService.class);

    private final MeasureRepository measureRepo;
    private final AlarmRepository   alarmRepo;

    public TimeSeriesService(MeasureRepository measureRepo, AlarmRepository alarmRepo) {
        this.measureRepo = measureRepo;
        this.alarmRepo   = alarmRepo;
    }

    @Transactional
    public void persist(String deviceId, DecodedPacket packet) {
        if (packet == null) return;

        if (!packet.measures().isEmpty()) {
            List<MeasureEntity> rows = packet.measures().stream()
                    .map(m -> new MeasureEntity(deviceId, m))
                    .toList();
            measureRepo.saveAll(rows);
        }

        if (!packet.alarms().isEmpty()) {
            List<AlarmEntity> rows = packet.alarms().stream()
                    .map(a -> new AlarmEntity(deviceId, a))
                    .toList();
            alarmRepo.saveAll(rows);
        }

        log.debug("Persisted {} measures and {} alarms for device {}",
                packet.measures().size(), packet.alarms().size(), deviceId);
    }
}
