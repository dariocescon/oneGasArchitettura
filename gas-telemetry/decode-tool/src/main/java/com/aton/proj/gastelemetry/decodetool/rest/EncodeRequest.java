package com.aton.proj.gastelemetry.decodetool.rest;

import java.util.List;
import java.util.Map;

/**
 * Body della richiesta POST /api/encode.
 *
 * <p>Esempio:
 * <pre>{@code
 * {
 *   "deviceId":   "0864431047987054",
 *   "deviceType": "TEK822V2",
 *   "password":   "TEK822",          // opzionale, default "TEK822"
 *   "commands": [
 *     { "type": "SET_INTERVAL", "parameters": { "interval": 4, "samplingPeriod": 1 } },
 *     { "type": "SET_CONTROL2_CONFIG", "parameters": { "adcRaw": true } }
 *   ]
 * }
 * }</pre>
 *
 * @param deviceId   identificatore del device target (IMEI o alias). Usato solo
 *                   per la preview di persistenza, non per la composizione ASCII
 * @param deviceType es. "TEK822V2"
 * @param password   password unità (default {@code "TEK822"} se omessa)
 * @param commands   lista di comandi da encodare in ordine
 */
public record EncodeRequest(
        String deviceId,
        String deviceType,
        String password,
        List<CommandSpec> commands
) {

    /**
     * Singolo comando: tipo + mappa di parametri liberi (chiave → valore).
     *
     * @param type       costante di {@code Tek822Encoder} (es. "SET_INTERVAL")
     * @param parameters parametri richiesti dal singolo encoder method
     */
    public record CommandSpec(String type, Map<String, Object> parameters) {}
}
