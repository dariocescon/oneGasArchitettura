package com.aton.proj.gastelemetry.common;

public interface DecoderContext {

    /** Configurazione globale (es. fuso orario, versione protocollo). */
    <T> T getConfig(String cfgKey);

    /** Configurazione specifica per device (es. soglia allarme livello serbatoio). */
    <T> T getConfig(String deviceId, String cfgKey);

    /** Scrive le misure e gli allarmi decodificati su TimeScaleDB. */
    void publishDecodedData(String deviceId, DecodedPacket packet);
}
