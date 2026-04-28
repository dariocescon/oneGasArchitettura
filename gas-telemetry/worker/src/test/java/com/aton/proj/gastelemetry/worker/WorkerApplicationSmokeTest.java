package com.aton.proj.gastelemetry.worker;

import com.aton.proj.gastelemetry.worker.publisher.LoggingPrimaryDataPublisher;
import com.aton.proj.gastelemetry.worker.publisher.PrimaryDataPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test che verifica il caricamento completo del context Spring del worker
 * in profilo "local" — senza Azure Event Hub, senza DB esterno.
 *
 * <p>Verifica:
 * <ul>
 *   <li>Tutti i bean si auto-wirano (CommandService, TcpServer, EventHubProducerConfig, ...)</li>
 *   <li>{@link PrimaryDataPublisher} risolto è l'impl no-op {@link LoggingPrimaryDataPublisher}</li>
 *   <li>TcpServer riesce a bindare la porta (uso 0 = porta libera assegnata dall'OS)</li>
 * </ul>
 */
@SpringBootTest(properties = "worker.tcp.port=0")
@ActiveProfiles("local")
class WorkerApplicationSmokeTest {

    @Autowired
    private PrimaryDataPublisher publisher;

    @Test
    @DisplayName("Context Spring del worker carica completamente in profilo 'local'")
    void contextLoads() {
        // Se siamo qui, tutti i @Component / @Bean si sono autowirati
    }

    @Test
    @DisplayName("In profilo 'local' il PrimaryDataPublisher è il no-op")
    void publisherIsNoOpInLocalProfile() {
        assertThat(publisher).isInstanceOf(LoggingPrimaryDataPublisher.class);
    }
}
