package com.aton.proj.gastelemetry.common;

/**
 * Insieme di comandi pendenti per un device.
 *
 * @param handle   identificatore del batch, usato da markCommandsSent() per
 *                 aggiornare lo stato di tutti i comandi del batch in una sola chiamata
 * @param commands array di comandi già codificati come stringhe pronte per l'invio
 */
public record CommandsPacket(int handle, String[] commands) {

    public boolean hasCommands() {
        return commands != null && commands.length > 0;
    }
}
