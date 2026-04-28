package com.aton.proj.gastelemetry.commandapi.rest;

import com.aton.proj.gastelemetry.persistence.AlarmEntity;
import com.aton.proj.gastelemetry.persistence.AlarmRepository;
import com.aton.proj.gastelemetry.persistence.MeasureEntity;
import com.aton.proj.gastelemetry.persistence.MeasureRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Endpoint di lettura della time-series TimescaleDB.
 *
 * <ul>
 *   <li>{@code GET /api/devices/{deviceId}/measures?from=&to=}</li>
 *   <li>{@code GET /api/devices/{deviceId}/alarms?from=&to=}</li>
 * </ul>
 *
 * <p>I parametri {@code from} e {@code to} sono ISO-8601 (es. {@code 2026-04-28T10:00:00Z}).
 * In assenza di intervallo si restituisce l'ultima ora (default ragionevole per UI live).
 */
@RestController
@RequestMapping("/api")
public class TimeSeriesController {

    private static final long DEFAULT_WINDOW_SECONDS = 3600;

    private final MeasureRepository measureRepo;
    private final AlarmRepository   alarmRepo;

    public TimeSeriesController(MeasureRepository measureRepo, AlarmRepository alarmRepo) {
        this.measureRepo = measureRepo;
        this.alarmRepo   = alarmRepo;
    }

    @GetMapping("/devices/{deviceId}/measures")
    public List<MeasureEntity> measures(
            @PathVariable String deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant[] window = resolveWindow(from, to);
        return measureRepo.findByDeviceIdAndTimestampBetweenOrderByTimestampAsc(
                deviceId, window[0], window[1]);
    }

    @GetMapping("/devices/{deviceId}/alarms")
    public List<AlarmEntity> alarms(
            @PathVariable String deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant[] window = resolveWindow(from, to);
        return alarmRepo.findByDeviceIdAndTimestampBetweenOrderByTimestampAsc(
                deviceId, window[0], window[1]);
    }

    private Instant[] resolveWindow(Instant from, Instant to) {
        Instant now = Instant.now();
        Instant resolvedTo   = (to != null) ? to : now;
        Instant resolvedFrom = (from != null) ? from : resolvedTo.minusSeconds(DEFAULT_WINDOW_SECONDS);
        return new Instant[]{resolvedFrom, resolvedTo};
    }
}
