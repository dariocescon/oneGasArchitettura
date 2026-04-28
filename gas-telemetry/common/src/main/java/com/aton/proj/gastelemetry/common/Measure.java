package com.aton.proj.gastelemetry.common;

import java.time.Instant;

/**
 * Una singola misura decodificata dal pacchetto del device.
 *
 * @param timestamp timestamp originale dal RTC del device (non il momento di ricezione)
 * @param obisCode  codice OBIS o identificatore della grandezza misurata (es. "1-0:1.8.0")
 * @param value     valore numerico della misura
 * @param unit      unità di misura (es. "m3", "°C", "mV")
 */
public record Measure(Instant timestamp, String obisCode, double value, String unit) {}
