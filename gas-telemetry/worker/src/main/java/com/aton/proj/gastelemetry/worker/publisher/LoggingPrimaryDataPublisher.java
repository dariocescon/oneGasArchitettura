package com.aton.proj.gastelemetry.worker.publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementazione no-op di {@link PrimaryDataPublisher}: logga la richiesta di pubblicazione
 * senza contattare Azure Event Hub.
 *
 * <p>Usata nel profilo Spring {@code local} per consentire l'avvio del worker senza
 * credenziali Azure (utile in sviluppo, CI, e per test integrati che non richiedono
 * un Event Hub reale).
 */
public class LoggingPrimaryDataPublisher implements PrimaryDataPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingPrimaryDataPublisher.class);

    @Override
    public void publish(String deviceId, byte[] data) {
        log.info("[NO-OP] publishPrimaryData(device={}, {} byte) — Event Hub disabilitato (profilo 'local')",
                deviceId, data.length);
    }
}
