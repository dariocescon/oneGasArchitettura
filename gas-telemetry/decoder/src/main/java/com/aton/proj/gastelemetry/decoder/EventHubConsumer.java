package com.aton.proj.gastelemetry.decoder;

import com.aton.proj.gastelemetry.common.Decoder;
import com.aton.proj.gastelemetry.common.DecoderContext;
import com.azure.messaging.eventhubs.CheckpointStore;
import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.EventProcessorClientBuilder;
import com.azure.messaging.eventhubs.models.ErrorContext;
import com.azure.messaging.eventhubs.models.EventContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Consuma eventi da Azure Event Hub e li passa a {@link Decoder#doDecode}.
 *
 * <p>{@link EventProcessorClient} è il modo raccomandato dall'Azure SDK per consumare
 * da Event Hub in modo scalabile: bilancia le partizioni tra più istanze del Decoder
 * usando il {@link CheckpointStore} iniettato.
 *
 * <p>Il bean è registrato SOLO se il profilo Spring attivo non è {@code local}:
 * in locale (sviluppo, CI) il consumer non si avvia e il decoder può essere caricato
 * senza credenziali Azure.
 */
@Component
@Profile("!local")
public class EventHubConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventHubConsumer.class);

    private final Decoder decoder;
    private final DecoderContext decoderContext;
    private final CheckpointStore checkpointStore;

    @Value("${eventhub.connection-string}")
    private String connectionString;

    @Value("${eventhub.name}")
    private String eventHubName;

    @Value("${eventhub.consumer-group:$Default}")
    private String consumerGroup;

    private EventProcessorClient processorClient;

    public EventHubConsumer(Decoder decoder,
                            DecoderContext decoderContext,
                            CheckpointStore checkpointStore) {
        this.decoder = decoder;
        this.decoderContext = decoderContext;
        this.checkpointStore = checkpointStore;
    }

    @PostConstruct
    public void start() {
        processorClient = new EventProcessorClientBuilder()
                .connectionString(connectionString, eventHubName)
                .consumerGroup(consumerGroup)
                .checkpointStore(checkpointStore)
                .processEvent(this::onEvent)
                .processError(this::onError)
                .buildEventProcessorClient();

        processorClient.start();
        log.info("Event Hub consumer started on hub '{}' group '{}'", eventHubName, consumerGroup);
    }

    @PreDestroy
    public void stop() {
        if (processorClient != null) {
            log.info("Stopping Event Hub consumer");
            processorClient.stop();
        }
    }

    private void onEvent(EventContext eventContext) {
        try {
            byte[] data = eventContext.getEventData().getBody();
            String deviceId = (String) eventContext.getEventData().getProperties().get("deviceId");

            if (deviceId == null || deviceId.isBlank()) {
                log.warn("Received event without deviceId property, skipping");
                return;
            }

            decoder.doDecode(decoderContext, deviceId, data);

            // Aggiorna il checkpoint così, in caso di restart, non si riprocessano eventi già gestiti
            eventContext.updateCheckpoint();

        } catch (Exception e) {
            log.error("Error processing event from partition {}",
                    eventContext.getPartitionContext().getPartitionId(), e);
        }
    }

    private void onError(ErrorContext errorContext) {
        log.error("Event Hub error on partition {}: {}",
                errorContext.getPartitionContext().getPartitionId(),
                errorContext.getThrowable().getMessage(),
                errorContext.getThrowable());
    }
}
