package com.aton.proj.gastelemetry.common;

import java.util.List;

/**
 * Risultato completo della decodifica di un pacchetto.
 * Viene prodotto dal Decoder e scritto su TimeScaleDB via DecoderContext.
 */
public record DecodedPacket(List<Measure> measures, List<Alarm> alarms) {}
