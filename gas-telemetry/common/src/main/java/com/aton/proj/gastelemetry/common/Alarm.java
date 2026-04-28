package com.aton.proj.gastelemetry.common;

import java.time.Instant;

/**
 * Un allarme generato durante la decodifica del pacchetto.
 * Può essere un allarme contenuto nel pacchetto stesso (device-side)
 * oppure calcolato dal Decoder in base alla configurazione (es. livello sotto soglia).
 *
 * @param timestamp   timestamp dell'evento, dal RTC del device se disponibile
 * @param alarmCode   codice identificativo dell'allarme
 * @param description descrizione leggibile
 */
public record Alarm(Instant timestamp, String alarmCode, String description) {}
