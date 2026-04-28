package com.aton.proj.gastelemetry.persistence;

/**
 * Stati del ciclo di vita di un comando inviato a un device.
 *
 * <pre>
 *   PENDING ──claimPending()──→ IN_PROGRESS ──markBatchAsSent()──→ SENT
 *                                    │
 *                                    └──markBatchAsFailed()──→ FAILED
 * </pre>
 *
 * Note:
 * <ul>
 *   <li>{@code IN_PROGRESS} è uno stato transitorio: tipicamente vive solo
 *       per la durata di una connessione TCP (millisecondi).</li>
 *   <li>Se il worker crasha tra {@code sendToDevice} e
 *       {@link CommandRepository#markBatchAsSent}, i comandi restano IN_PROGRESS.
 *       TODO: schedulare un job che li riporti a PENDING dopo un timeout
 *       (~5 minuti, basato su {@code updated_at}).</li>
 *   <li>Lo stato {@code DELIVERED} non è previsto: richiederebbe un ack dal
 *       device che il protocollo TEK822 non fornisce. {@code SENT} esprime
 *       già "byte usciti correttamente sul socket TCP".</li>
 * </ul>
 */
public enum CommandStatus {
    PENDING,
    IN_PROGRESS,
    SENT,
    FAILED
}
