package com.aton.proj.gastelemetry.common;

public interface Worker {

    /**
     * Validazione strutturale prima di processare il pacchetto.
     * Override nelle implementazioni device-specific per verificare:
     * - lunghezza minima del payload
     * - product type riconosciuto
     * - campo lunghezza interno coerente con i byte ricevuti
     * Se non è disponibile un checksum esplicito, questo è il punto
     * in cui fare le verifiche strutturali del protocollo.
     */
    default boolean validate(byte[] data) {
        return data != null && data.length > 0;
    }

    /**
     * Elabora un pacchetto grezzo ricevuto dal device.
     * Il contratto è:
     *   1. Estrarre deviceId dal payload
     *   2. (Opzionale) Decriptare se il protocollo lo richiede
     *   3. Pubblicare il dato in chiaro sul Bus via ctx.publishPrimaryData()
     *   4. Recuperare i comandi pendenti via ctx.getCommands()
     *   5. Codificarli e inviarli al device via ctx.sendToDevice()
     *   6. Confermare l'invio via ctx.markCommandsSent()
     */
    void doWork(WorkerContext ctx, byte[] data);
}
