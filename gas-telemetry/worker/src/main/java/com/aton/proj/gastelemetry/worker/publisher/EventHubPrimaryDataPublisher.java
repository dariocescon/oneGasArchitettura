package com.aton.proj.gastelemetry.worker.publisher;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Implementazione "reale" di {@link PrimaryDataPublisher} che pubblica su Azure Event Hub.
 *
 * <p>Il {@code deviceId} viene messo come property dell'evento per consentire al
 * Decoder di ricostruirlo senza riparsare il payload.
 *
 * <p>Bean creato condizionalmente da {@code EventHubProducerConfig} solo se NON si
 * è nel profilo {@code local}.
 */
public class EventHubPrimaryDataPublisher implements PrimaryDataPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventHubPrimaryDataPublisher.class);

    private final EventHubProducerClient client;

    public EventHubPrimaryDataPublisher(EventHubProducerClient client) {
        this.client = client;
    }

    @Override
    public void publish(String deviceId, byte[] data) {
        EventData event = new EventData(data);
        event.getProperties().put("deviceId", deviceId);
        client.send(List.of(event));
        log.debug("Published {} bytes for device {} to Event Hub", data.length, deviceId);
    }
}
