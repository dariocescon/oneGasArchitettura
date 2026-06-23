package com.aton.proj.gastelemetry.decodetool.config;

import com.aton.proj.gastelemetry.decoder.impl.Tek822Decoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Espone {@link Tek822Decoder} come bean Spring nel contesto del decode-tool.
 *
 * <p>Il decoder è una classe @Component nel modulo originale, ma qui evitiamo
 * il component-scan di {@code com.aton.proj.gastelemetry.decoder} per non
 * tirarsi dietro {@code EventHubConsumer}, {@code MissedTransmissionDetector},
 * {@code DecoderContextImpl} (che richiedono JPA / Azure SDK).
 *
 * <p>La classe {@link Tek822Decoder} non ha dipendenze costruttore: si può
 * istanziare con {@code new Tek822Decoder()} senza problemi.
 */
@Configuration
public class DecoderBeansConfig {

    @Bean
    public Tek822Decoder tek822Decoder() {
        return new Tek822Decoder();
    }
}
