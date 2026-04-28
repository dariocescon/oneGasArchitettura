package com.aton.proj.gastelemetry.persistence;

import com.aton.proj.gastelemetry.common.Alarm;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Allarme time-series persistito nella hypertable {@code device_alarms}.
 * Stesso modello di {@link MeasureEntity}: chunk temporali di 7 giorni in prod,
 * tabella semplice in test.
 */
@Entity
@Table(
        name = "device_alarms",
        indexes = {
                @Index(name = "ix_device_alarms_device_ts", columnList = "device_id, timestamp DESC")
        }
)
public class AlarmEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 50)
    private String deviceId;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "alarm_code", nullable = false, length = 50)
    private String alarmCode;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    public AlarmEntity() {}

    public AlarmEntity(String deviceId, Alarm a) {
        this.deviceId = deviceId;
        this.timestamp = a.timestamp();
        this.alarmCode = a.alarmCode();
        this.description = a.description();
    }

    public Long getId()                       { return id; }
    public String getDeviceId()               { return deviceId; }
    public Instant getTimestamp()             { return timestamp; }
    public String getAlarmCode()              { return alarmCode; }
    public String getDescription()            { return description; }

    public void setId(Long id)                { this.id = id; }
    public void setDeviceId(String deviceId)  { this.deviceId = deviceId; }
    public void setTimestamp(Instant ts)     { this.timestamp = ts; }
    public void setAlarmCode(String code)    { this.alarmCode = code; }
    public void setDescription(String desc)  { this.description = desc; }
}
