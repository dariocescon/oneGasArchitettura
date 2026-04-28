package com.aton.proj.gastelemetry.worker.publisher;

/**
 * Astrazione del canale di pubblicazione del payload "primario" (raw data ricevuto
 * dai device).
 *
 * <p>Disaccoppia {@code WorkerContextImpl} dall'SDK Azure: in produzione l'implementazione
 * userà {@code EventHubProducerClient}; in locale/test si può iniettare un'implementazione
 * no-op che logga senza inviare nulla.
 *
 * <p>Le implementazioni devono essere thread-safe: lo stesso bean singleton viene
 * condiviso da tutti i {@code WorkerContextImpl} per-connessione.
 */
public interface PrimaryDataPublisher {

    /**
     * Pubblica il payload grezzo associato a un device.
     *
     * @param deviceId IMEI/identificativo del device (usato come property dell'evento per il routing)
     * @param data     payload binario originale ricevuto dal device
     */
    void publish(String deviceId, byte[] data);
}
