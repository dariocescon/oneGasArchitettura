package com.aton.proj.gastelemetry.decodetool.service;

import com.aton.proj.gastelemetry.common.DecodedPacket;
import com.aton.proj.gastelemetry.common.DecoderContext;

/**
 * Implementazione di {@link DecoderContext} che <b>cattura</b> il packet
 * decodificato anziché pubblicarlo (su Event Hub / TimeScaleDB).
 *
 * <p>Usato dal decode-tool per ottenere il risultato della decodifica come
 * valore di ritorno della chiamata REST. Non è thread-safe: va istanziato
 * una nuova volta per ogni richiesta.
 *
 * <p>{@link #getConfig(String)} e {@link #getConfig(String, String)} restituiscono
 * sempre {@code null}: il tool non valuta AlarmRule server-side (decisione di
 * design — vedi documentazione utente).
 */
public class InMemoryDecoderContext implements DecoderContext {

    private DecodedPacket captured;
    private String capturedDeviceId;

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String cfgKey) {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String deviceId, String cfgKey) {
        return null;
    }

    @Override
    public void publishDecodedData(String deviceId, DecodedPacket packet) {
        this.capturedDeviceId = deviceId;
        this.captured = packet;
    }

    public DecodedPacket getCaptured() {
        return captured;
    }

    public String getCapturedDeviceId() {
        return capturedDeviceId;
    }
}
