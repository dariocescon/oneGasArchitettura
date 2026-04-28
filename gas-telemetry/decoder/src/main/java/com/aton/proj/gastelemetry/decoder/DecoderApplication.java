package com.aton.proj.gastelemetry.decoder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Vedi {@code WorkerApplication} per la motivazione di {@link EntityScan}/
 * {@link EnableJpaRepositories}: estendono lo scan di Hibernate / Spring Data JPA
 * al modulo {@code gas-telemetry-persistence}.
 */
@SpringBootApplication
@EntityScan(basePackages = "com.aton.proj.gastelemetry.persistence")
@EnableJpaRepositories(basePackages = "com.aton.proj.gastelemetry.persistence")
@EnableScheduling
public class DecoderApplication {

    public static void main(String[] args) {
        SpringApplication.run(DecoderApplication.class, args);
    }
}
