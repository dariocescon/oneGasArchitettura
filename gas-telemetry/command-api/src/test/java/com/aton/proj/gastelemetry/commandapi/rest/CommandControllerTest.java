package com.aton.proj.gastelemetry.commandapi.rest;

import com.aton.proj.gastelemetry.commandapi.dto.CreateCommandRequest;
import com.aton.proj.gastelemetry.persistence.CommandEntity;
import com.aton.proj.gastelemetry.persistence.CommandRepository;
import com.aton.proj.gastelemetry.persistence.CommandStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test end-to-end del {@code CommandController}: parte un Tomcat embedded su
 * porta random e invoca gli endpoint via {@link TestRestTemplate}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CommandControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private CommandRepository repo;

    @Autowired
    private ObjectMapper mapper;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    @DisplayName("POST /api/commands inserisce una riga PENDING e ne ritorna la view")
    void createCommand() {
        CreateCommandRequest req = new CreateCommandRequest(
                "111111111111111", "TEK822V1", "SET_INTERVAL",
                Map.of("interval", 4, "samplingPeriod", 1));

        ResponseEntity<Map<String, Object>> resp = rest.exchange(
                url("/api/commands"), HttpMethod.POST,
                jsonEntity(req),
                new org.springframework.core.ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        Long id = ((Number) resp.getBody().get("id")).longValue();
        assertThat(resp.getBody().get("status")).isEqualTo("PENDING");

        // Verifica DB: il JSON dei params è stato serializzato correttamente
        CommandEntity saved = repo.findById(id).orElseThrow();
        assertThat(saved.getCommandType()).isEqualTo("SET_INTERVAL");
        Map<String, Object> params = saved.parseParams(mapper);
        assertThat(params).containsEntry("interval", 4);
    }

    @Test
    @DisplayName("POST /api/commands con campi obbligatori mancanti → 400")
    void createCommand_validation() {
        CreateCommandRequest bad = new CreateCommandRequest(
                "111111111111111", null, "SET_INTERVAL", Map.of());

        ResponseEntity<String> resp = rest.exchange(
                url("/api/commands"), HttpMethod.POST,
                jsonEntity(bad), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("GET /api/commands/{id} restituisce 404 per id inesistente")
    void getCommand_notFound() {
        ResponseEntity<String> resp = rest.getForEntity(url("/api/commands/99999"), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /api/devices/{deviceId}/commands?status=PENDING filtra per stato")
    void listForDevice() {
        // Pre-popolamento: 2 PENDING + 1 SENT per lo stesso device
        save("DEV_LIST", "REBOOT", CommandStatus.PENDING);
        save("DEV_LIST", "REQUEST_STATUS", CommandStatus.PENDING);
        save("DEV_LIST", "OLD", CommandStatus.SENT);

        ResponseEntity<List<Map<String, Object>>> resp = rest.exchange(
                url("/api/devices/DEV_LIST/commands?status=PENDING"),
                HttpMethod.GET, null,
                new org.springframework.core.ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(2);
        assertThat(resp.getBody()).extracting(m -> m.get("commandType"))
                .containsExactlyInAnyOrder("REBOOT", "REQUEST_STATUS");
    }

    private void save(String deviceId, String type, CommandStatus status) {
        CommandEntity e = new CommandEntity();
        e.setDeviceId(deviceId);
        e.setDeviceType("TEK822V1");
        e.setCommandType(type);
        e.setStatus(status);
        repo.saveAndFlush(e);
    }

    private static <T> HttpEntity<T> jsonEntity(T body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, h);
    }
}
