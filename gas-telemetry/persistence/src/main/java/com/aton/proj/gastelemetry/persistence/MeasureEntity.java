package com.aton.proj.gastelemetry.persistence;

import com.aton.proj.gastelemetry.common.Measure;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Misura time-series persistita nella hypertable {@code device_measures}.
 *
 * <p>In produzione è una <i>hypertable TimescaleDB</i> partizionata sulla colonna
 * {@code timestamp} (chunk di 7 giorni). L'entity JPA non sa nulla del partizionamento;
 * Hibernate emette INSERT, TimescaleDB li redirige al chunk corretto.
 *
 * <p>PK surrogata {@code BIGSERIAL}; pattern di accesso reale = query time-window
 * con filter {@code device_id} (servito dall'indice {@code (device_id, timestamp DESC)}).
 *
 * <p><b>H2/test:</b> in test la tabella esiste come tabella normale (ddl-auto crea
 * da @Entity), senza partizionamento — behavior ORM identico, solo perf produttive cambiano.
 */
@Entity
@Table(
        name = "device_measures",
        indexes = {
                @Index(name = "ix_device_measures_device_ts", columnList = "device_id, timestamp DESC")
        }
)
public class MeasureEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 50)
    private String deviceId;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "obis_code", nullable = false, length = 50)
    private String obisCode;

    /** Colonna {@code measure_value}: "value" è reserved keyword in H2/SQL standard. */
    @Column(name = "measure_value", nullable = false)
    private double value;

    @Column(name = "unit", length = 20)
    private String unit;

    public MeasureEntity() {}

    public MeasureEntity(String deviceId, Measure m) {
        this.deviceId = deviceId;
        this.timestamp = m.timestamp();
        this.obisCode = m.obisCode();
        this.value = m.value();
        this.unit = m.unit();
    }

    public Long getId()                       { return id; }
    public String getDeviceId()               { return deviceId; }
    public Instant getTimestamp()             { return timestamp; }
    public String getObisCode()               { return obisCode; }
    public double getValue()                  { return value; }
    public String getUnit()                   { return unit; }

    public void setId(Long id)                { this.id = id; }
    public void setDeviceId(String deviceId)  { this.deviceId = deviceId; }
    public void setTimestamp(Instant ts)     { this.timestamp = ts; }
    public void setObisCode(String code)     { this.obisCode = code; }
    public void setValue(double value)       { this.value = value; }
    public void setUnit(String unit)         { this.unit = unit; }
}
