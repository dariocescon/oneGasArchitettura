package com.aton.proj.gastelemetry.decodetool.rest;

import java.time.Instant;

/**
 * Wrapper di una {@code Measure} arricchito con i byte di payload da cui
 * il valore è stato estratto.
 *
 * @param timestamp  timestamp della misura
 * @param obisCode   identificatore semantico
 * @param value      valore numerico decodificato
 * @param unit       unità di misura o label umano (es. "BG96", "FW 3.0")
 * @param sourceHex  byte del payload originale (es. "0A", "0932").
 *                   {@code null} se l'offset è sconosciuto.
 * @param byteRange  posizione dei byte sorgente nel payload, formato
 *                   {@code "N"} per singolo byte o {@code "N-M"} per range
 *                   (es. "7-14", "15-16"). {@code null} se sconosciuta.
 */
public record MeasureView(
        Instant timestamp,
        String obisCode,
        double value,
        String unit,
        String sourceHex,
        String byteRange
) {}
