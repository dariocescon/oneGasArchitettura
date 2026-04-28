package com.aton.proj.gastelemetry.commandapi.rest;

import com.aton.proj.gastelemetry.persistence.ConfigEntity;
import com.aton.proj.gastelemetry.persistence.ConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test end-to-end di {@code ConfigController}.
 * Verifica PUT/GET/DELETE su {@code device_config}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ConfigRepository repo;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    @DisplayName("PUT crea poi GET legge il valore")
    void putAndGet() {
        ResponseEntity<Map<String, Object>> put = rest.exchange(
                url("/api/config/DEV1/tank.threshold"),
                HttpMethod.PUT, plainTextEntity("35"),
                new org.springframework.core.ParameterizedTypeReference<>() {});

        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(put.getBody()).containsEntry("configValue", "35");

        ResponseEntity<Map<String, Object>> get = rest.exchange(
                url("/api/config/DEV1/tank.threshold"),
                HttpMethod.GET, null,
                new org.springframework.core.ParameterizedTypeReference<>() {});

        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get.getBody()).containsEntry("configValue", "35");
    }

    @Test
    @DisplayName("PUT secondo valore aggiorna in-place senza creare nuova riga")
    void putUpdatesInPlace() {
        rest.exchange(url("/api/config/DEV2/threshold"),
                HttpMethod.PUT, plainTextEntity("10"), Void.class);
        rest.exchange(url("/api/config/DEV2/threshold"),
                HttpMethod.PUT, plainTextEntity("20"), Void.class);

        assertThat(repo.findForDevice("DEV2", "threshold"))
                .get()
                .extracting(ConfigEntity::getConfigValue)
                .isEqualTo("20");
    }

    @Test
    @DisplayName("GET su chiave inesistente → 404")
    void getNotFound() {
        ResponseEntity<String> resp = rest.getForEntity(
                url("/api/config/UNKNOWN/none"), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("DELETE rimuove la riga e ritorna 204")
    void delete() {
        rest.exchange(url("/api/config/DEV3/k"),
                HttpMethod.PUT, plainTextEntity("v"), Void.class);

        ResponseEntity<Void> del = rest.exchange(url("/api/config/DEV3/k"),
                HttpMethod.DELETE, null, Void.class);

        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(repo.findForDevice("DEV3", "k")).isEmpty();
    }

    private static HttpEntity<String> plainTextEntity(String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.TEXT_PLAIN);
        return new HttpEntity<>(body, h);
    }
}
