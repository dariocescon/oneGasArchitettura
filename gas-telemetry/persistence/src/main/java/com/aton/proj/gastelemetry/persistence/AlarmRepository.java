package com.aton.proj.gastelemetry.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository per {@link AlarmEntity}.
 */
@Repository
public interface AlarmRepository extends JpaRepository<AlarmEntity, Long> {

    List<AlarmEntity> findByDeviceIdAndTimestampBetweenOrderByTimestampAsc(
            String deviceId, Instant from, Instant to);
}
