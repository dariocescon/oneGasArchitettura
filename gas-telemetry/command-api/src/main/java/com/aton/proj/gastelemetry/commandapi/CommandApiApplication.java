package com.aton.proj.gastelemetry.commandapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Modulo REST API che espone HTTP endpoint sopra le entity di
 * {@code gas-telemetry-persistence}. Non parla con i device — è il punto
 * d'ingresso per applicazioni di front-end / amministrazione / integrazione.
 */
@SpringBootApplication
@EntityScan(basePackages = "com.aton.proj.gastelemetry.persistence")
@EnableJpaRepositories(basePackages = "com.aton.proj.gastelemetry.persistence")
public class CommandApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommandApiApplication.class, args);
    }
}
