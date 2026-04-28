package com.aton.proj.gastelemetry.worker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provider esplicito dell'{@link ObjectMapper} per il worker.
 *
 * <p>Spring Boot {@code JacksonAutoConfiguration} crea l'ObjectMapper solo se è
 * disponibile {@code Jackson2ObjectMapperBuilder} (che vive in {@code spring-web}).
 * Il worker NON include {@code spring-boot-starter-web} (è un server TCP),
 * quindi l'auto-config non scatta e dobbiamo dichiarare il bean a mano.
 *
 * <p>Usato da {@code CommandService} per (de)serializzare {@code command_params} (JSON).
 */
@Configuration
public class JacksonConfig {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
