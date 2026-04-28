package com.aton.proj.gastelemetry.worker.impl;

import java.util.HashMap;
import java.util.Map;

/**
 * Value object che rappresenta un singolo comando da inviare al device.
 *
 * Corrisponde a una riga della tabella comandi nel DB (CommandEntity).
 * Viene usato da Tek822Encoder per produrre le stringhe HEX da trasmettere.
 *
 * Il campo {@code id} è null per i comandi sintetici (es. REBOOT
 * auto-aggiunto da Tek822Encoder quando ci sono S-commands).
 */
public record CommandEntry(
        Long id,
        String deviceId,
        String deviceType,
        String commandType,
        Map<String, Object> parameters
) {
    /**
     * Costruttore comodo per comandi sintetici (senza ID DB, senza parametri iniziali).
     * I parametri possono essere aggiunti successivamente tramite {@link #parameters()}.
     */
    public CommandEntry(String deviceId, String deviceType, String commandType) {
        this(null, deviceId, deviceType, commandType, new HashMap<>());
    }

    /** Restituisce il valore del parametro, o null se assente. */
    public Object getParam(String key) {
        return parameters.get(key);
    }

    /** Restituisce il valore del parametro, o {@code defaultValue} se assente. */
    public Object getParamOrDefault(String key, Object defaultValue) {
        return parameters.getOrDefault(key, defaultValue);
    }
}
