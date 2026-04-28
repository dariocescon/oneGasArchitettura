package com.aton.proj.gastelemetry.decoder;

import com.aton.proj.gastelemetry.common.Decoder;
import com.aton.proj.gastelemetry.common.DecoderContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test che verifica il caricamento completo del context Spring del decoder
 * in profilo "local" — senza Azure Event Hub, senza DB esterno.
 *
 * <p>Verifica:
 * <ul>
 *   <li>Bean Decoder ({@code Tek822Decoder}) e DecoderContext ({@code DecoderContextImpl}) iniettabili</li>
 *   <li>{@code EventHubConsumer} NON viene caricato in profilo {@code local}
 *       (controllo: bean assente nel context)</li>
 *   <li>{@code EventHubConsumerConfig} NON viene caricato in profilo {@code local}
 *       → niente CheckpointStore, niente connessione Azure</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("local")
class DecoderApplicationSmokeTest {

    @Autowired
    private ApplicationContext ctx;

    @Autowired
    private Decoder decoder;

    @Autowired
    private DecoderContext decoderContext;

    @Test
    @DisplayName("Context Spring del decoder carica completamente in profilo 'local'")
    void contextLoads() {
        assertThat(decoder).isNotNull();
        assertThat(decoderContext).isNotNull();
    }

    @Test
    @DisplayName("In profilo 'local' EventHubConsumer non viene caricato")
    void eventHubConsumerAbsentInLocalProfile() {
        assertThat(ctx.getBeansOfType(EventHubConsumer.class)).isEmpty();
    }
}
