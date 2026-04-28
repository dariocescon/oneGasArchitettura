package com.aton.proj.gastelemetry.common;

public interface WorkerContext {

    /** Pubblica il payload grezzo (cleartext) su Azure Event Hub. */
    void publishPrimaryData(String deviceId, byte[] data);

    /**
     * Recupera i comandi pendenti per il device in modo atomico:
     * l'implementazione deve marcare i comandi come IN_PROGRESS
     * prima di restituirli, così un secondo Worker concorrente
     * non li prende una seconda volta.
     */
    CommandsPacket getCommands(String deviceId);

    /** Marca i comandi identificati da handle come SENT nel database. */
    void markCommandsSent(int handle);

    /**
     * Invia i byte codificati al device sulla connessione corrente.
     * L'implementazione dipende dal trasporto:
     *   - TCP: scrive sull'OutputStream del socket
     *   - UDP: invia un datagramma di risposta
     * Questo metodo è chiamato dal Worker solo se ci sono comandi da inviare.
     */
    void sendToDevice(byte[] data);
}
