package com.aton.proj.gastelemetry.decoder.config;

import com.aton.proj.gastelemetry.decoder.checkpoint.InMemoryCheckpointStore;
import com.azure.messaging.eventhubs.CheckpointStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configurazione del lato "consumer" Event Hub del Decoder.
 *
 * <p>Fornisce il bean {@link CheckpointStore} richiesto da
 * {@code EventProcessorClientBuilder.checkpointStore(...)}.
 *
 * <p><b>Solo profili NON-local</b>: in profilo {@code local} il bean
 * {@code EventHubConsumer} non viene caricato e quindi il checkpoint store
 * non serve.
 *
 * <p><b>Default attuale</b>: {@link InMemoryCheckpointStore} — non sopravvive a
 * restart, non scalabile su più istanze. Quando il task <i>Checkpoint store</i>
 * della roadmap verrà completato, sostituire con {@code BlobCheckpointStore}
 * (azure-messaging-eventhubs-checkpointstore-blob).
 */
@Configuration
@Profile("!local")
public class EventHubConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(EventHubConsumerConfig.class);

    @Bean
    public CheckpointStore checkpointStore() {
        log.warn("Uso InMemoryCheckpointStore (placeholder). Sostituire con BlobCheckpointStore "
                + "appena il task 'Checkpoint store' della roadmap viene approvato.");
        return new InMemoryCheckpointStore();
    }
}
