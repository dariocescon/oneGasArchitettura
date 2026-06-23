package com.aton.proj.gastelemetry.decodetool.rest;

import java.time.Instant;

/**
 * Wrapper di un {@code Alarm} arricchito con i byte di payload sorgente.
 *
 * <p>Per gli allarmi auto-segnalati dal device (Limit 1/2/3, Bund), il byte
 * sorgente è sempre il byte 4 dell'header (Alarm/Status bitmask).
 *
 * @param timestamp   timestamp dell'allarme
 * @param alarmCode   codice (es. "DEVICE_LIMIT_1")
 * @param description descrizione human-readable
 * @param sourceHex   byte hex sorgente (per allarmi device = byte 4)
 * @param byteRange   posizione del byte sorgente (per allarmi device = "4")
 */
public record AlarmView(
        Instant timestamp,
        String alarmCode,
        String description,
        String sourceHex,
        String byteRange
) {}
