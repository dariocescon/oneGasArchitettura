package com.aton.proj.gastelemetry.commandapi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test che verifica il caricamento completo del context Spring del command-api.
 *
 * <p>Il context deve includere automaticamente:
 * <ul>
 *   <li>Tutti i repository di {@code com.aton.proj.gastelemetry.persistence}
 *       (via @EnableJpaRepositories sul main)</li>
 *   <li>I 3 controller REST</li>
 *   <li>Embedded Tomcat</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CommandApiApplicationSmokeTest {

    @Test
    @DisplayName("Context Spring del command-api carica completamente")
    void contextLoads() {
        // Se @SpringBootTest fallisce nello startup, il test fallisce.
    }
}
