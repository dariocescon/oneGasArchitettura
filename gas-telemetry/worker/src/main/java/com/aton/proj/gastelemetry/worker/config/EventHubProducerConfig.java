package com.aton.proj.gastelemetry.worker.config;

import com.aton.proj.gastelemetry.worker.publisher.EventHubPrimaryDataPublisher;
import com.aton.proj.gastelemetry.worker.publisher.LoggingPrimaryDataPublisher;
import com.aton.proj.gastelemetry.worker.publisher.PrimaryDataPublisher;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configurazione del bean {@link PrimaryDataPublisher} con selezione per profilo Spring:
 *
 * <ul>
 *   <li>profilo {@code local} → {@link LoggingPrimaryDataPublisher} (no-op, logga e basta)</li>
 *   <li>tutti gli altri profili → {@link EventHubPrimaryDataPublisher} con
 *       {@code EventHubProducerClient} reale costruito da connection string + nome hub</li>
 * </ul>
 *
 * In profilo non-local le property {@code eventhub.connection-string} ed
 * {@code eventhub.name} sono obbligatorie: l'avvio fallisce fast se mancano.
 */
@Configuration
public class EventHubProducerConfig {

    private static final Logger log = LoggerFactory.getLogger(EventHubProducerConfig.class);

    /** Tenuto per il {@link #close()} su shutdown del context. Null se profilo local. */
    private EventHubProducerClient producerClient;

    @Bean
    @Profile("!local")
    public PrimaryDataPublisher eventHubPrimaryDataPublisher(
            @Value("${eventhub.connection-string}") String connectionString,
            @Value("${eventhub.name}") String eventHubName) {

        log.info("Configuro Event Hub producer reale (hub='{}')", eventHubName);
        producerClient = new EventHubClientBuilder()
                .connectionString(connectionString, eventHubName)
                .buildProducerClient();
        return new EventHubPrimaryDataPublisher(producerClient);
    }

    @Bean
    @Profile("local")
    public PrimaryDataPublisher loggingPrimaryDataPublisher() {
        log.warn("Profilo 'local' attivo: PrimaryDataPublisher in modalità no-op — nessuna connessione Event Hub");
        return new LoggingPrimaryDataPublisher();
    }

    @PreDestroy
    public void closeProducer() {
        if (producerClient != null) {
            log.info("Chiusura Event Hub producer client");
            producerClient.close();
        }
    }
}
