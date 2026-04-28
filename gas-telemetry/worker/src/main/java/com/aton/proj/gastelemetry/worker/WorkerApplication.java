package com.aton.proj.gastelemetry.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Le entity e i repository condivisi vivono nel modulo {@code gas-telemetry-persistence}
 * (package {@code com.aton.proj.gastelemetry.persistence}). Per estendere lo scan
 * di Hibernate e Spring Data JPA oltre il package del worker servono le annotation
 * esplicite — {@link SpringBootApplication} di default scansiona solo il proprio package.
 */
@SpringBootApplication
@EntityScan(basePackages = "com.aton.proj.gastelemetry.persistence")
@EnableJpaRepositories(basePackages = "com.aton.proj.gastelemetry.persistence")
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
