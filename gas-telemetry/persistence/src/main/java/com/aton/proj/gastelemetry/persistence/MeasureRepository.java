package com.aton.proj.gastelemetry.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository per {@link MeasureEntity}.
 *
 * <p>Le scritture passano da {@code saveAll} (batch insert: vedi
 * {@code TimeSeriesService} nel decoder). Le query temporali derivate sono
 * usate dal modulo {@code command-api} per gli endpoint REST di lettura.
 */
@Repository
public interface MeasureRepository extends JpaRepository<MeasureEntity, Long> {

    List<MeasureEntity> findByDeviceIdAndTimestampBetweenOrderByTimestampAsc(
            String deviceId, Instant from, Instant to);

    /**
     * Per ogni device, restituisce {@code [deviceId, lastTimestamp]} dell'ultima
     * misura registrata. Usato dal {@code MissedTransmissionDetector}.
     *
     * <p>In produzione su TimescaleDB la query è efficiente grazie all'indice
     * {@code (device_id, timestamp DESC)}: planner usa skip-scan / loose index scan.
     */
    @Query("SELECT m.deviceId, MAX(m.timestamp) FROM MeasureEntity m GROUP BY m.deviceId")
    List<Object[]> findLastTimestampPerDevice();
}
