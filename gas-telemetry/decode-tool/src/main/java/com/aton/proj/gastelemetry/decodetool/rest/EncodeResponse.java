package com.aton.proj.gastelemetry.decodetool.rest;

import java.util.List;
import java.util.Map;

/**
 * Body della risposta a POST /api/encode.
 *
 * <p>Espone 3 viste dello stesso flusso:
 * <ol>
 *   <li>i singoli comandi ASCII generati da {@code Tek822Encoder.encode(...)}
 *       (inclusi quelli auto-appended come REBOOT)</li>
 *   <li>il payload ASCII composto dal Worker via
 *       {@code Tek822Worker.composeAsciiPayload(...)} — quello che andrebbe sul
 *       wire TCP/GPRS, comprensivo di CRLF terminatore</li>
 *   <li>la rappresentazione hex dei byte del payload ASCII (utile per
 *       verifica byte-per-byte e log analysis)</li>
 *   <li>il preview delle righe che verrebbero persistite nella tabella
 *       {@code device_commands} (status PENDING) — utile per anticipare l'effetto
 *       di un dispatch via {@code command-api}</li>
 * </ol>
 *
 * @param individualCommands lista delle stringhe ASCII per ogni comando (post auto-append)
 * @param composedAscii      payload ASCII finale (con CRLF, password dedup-ata)
 * @param composedHex        rappresentazione hex (uppercase) dei byte ASCII
 * @param totalBytes         numero totale di byte del payload composto
 * @param persistenceRows    preview delle righe device_commands (status=PENDING)
 *                           NB: il REBOOT auto-appended NON è incluso qui, perché
 *                           viene aggiunto dal Worker al momento del dispatch (è
 *                           "sintetico", non risiede in DB)
 */
public record EncodeResponse(
        List<String> individualCommands,
        String composedAscii,
        String composedHex,
        int totalBytes,
        List<PersistenceRow> persistenceRows
) {

    /**
     * Preview di una riga {@code device_commands} così come verrebbe insertata.
     *
     * @param deviceId      target del comando
     * @param deviceType    es. "TEK822V2"
     * @param commandType   costante di {@code Tek822Encoder} (es. "SET_INTERVAL")
     * @param commandParams JSON serializzato dei parametri
     * @param status        sempre "PENDING" alla creazione
     */
    public record PersistenceRow(
            String deviceId,
            String deviceType,
            String commandType,
            String commandParams,
            String status
    ) {}
}
