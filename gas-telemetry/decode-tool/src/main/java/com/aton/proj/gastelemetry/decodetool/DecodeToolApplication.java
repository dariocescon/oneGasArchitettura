package com.aton.proj.gastelemetry.decodetool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Tool client-server di decodifica on-demand dei payload TEK822.
 *
 * <p>Espone una REST API ({@code POST /api/decode}) e una pagina HTML statica
 * sotto {@code /} che permette di incollare una stringa esadecimale
 * proveniente da un device Tekelek e visualizzarne la decodifica completa
 * (header, misure, allarmi auto-segnalati dal device).
 *
 * <p>Riusa {@code Tek822Decoder} dal modulo {@code gas-telemetry-decoder} —
 * vedi {@link DecoderBeansConfig}. Nessuna persistenza, nessun consumer
 * Event Hub: il packet decodificato viene catturato in memoria e restituito
 * come JSON nella response HTTP.
 */
@SpringBootApplication
public class DecodeToolApplication {

    public static void main(String[] args) {
        SpringApplication.run(DecodeToolApplication.class, args);
    }
}
