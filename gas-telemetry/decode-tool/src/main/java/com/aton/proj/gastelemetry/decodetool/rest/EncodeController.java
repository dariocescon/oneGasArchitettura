package com.aton.proj.gastelemetry.decodetool.rest;

import com.aton.proj.gastelemetry.worker.impl.CommandEntry;
import com.aton.proj.gastelemetry.worker.impl.Tek822Encoder;
import com.aton.proj.gastelemetry.worker.impl.Tek822Worker;
import com.aton.proj.gastelemetry.common.CommandsPacket;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST controller per la composizione di comandi TEK822.
 *
 * <p>L'endpoint {@code POST /api/encode} riceve una lista di
 * {@link EncodeRequest.CommandSpec} e restituisce:
 * <ul>
 *   <li>i singoli comandi ASCII generati dal {@link Tek822Encoder}</li>
 *   <li>il payload finale composto dal Worker (concatenazione + dedup password
 *       + CRLF) — ASCII e hex</li>
 *   <li>la preview delle righe {@code device_commands} che verrebbero
 *       persistite per dispatcciare i comandi tramite la pipeline ufficiale</li>
 * </ul>
 *
 * <p>Errori di parametri mancanti / command type sconosciuti restituiscono
 * HTTP 400 con body JSON {@code {error: "..."}}.
 */
@RestController
@RequestMapping("/api")
public class EncodeController {

    private static final Logger log = LoggerFactory.getLogger(EncodeController.class);

    private static final String DEFAULT_PASSWORD = "TEK822";
    private static final String DEFAULT_STATUS   = "PENDING";

    private final Tek822Encoder encoder;
    private final ObjectMapper mapper;

    public EncodeController(Tek822Encoder encoder, ObjectMapper mapper) {
        this.encoder = encoder;
        this.mapper  = mapper;
    }

    @PostMapping("/encode")
    public ResponseEntity<?> encode(@RequestBody EncodeRequest request) {
        if (request == null || request.commands() == null || request.commands().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Campo 'commands' mancante o vuoto"));
        }

        String password   = (request.password() == null || request.password().isBlank())
                ? DEFAULT_PASSWORD : request.password();
        String deviceId   = request.deviceId()   == null ? "" : request.deviceId();
        String deviceType = request.deviceType() == null ? "" : request.deviceType();

        // 1. Costruisce i CommandEntry attesi dal Tek822Encoder, iniettando la
        //    password come parametro speciale (l'encoder lo usa per il prefisso).
        List<CommandEntry> entries = new ArrayList<>();
        for (EncodeRequest.CommandSpec spec : request.commands()) {
            if (spec == null || spec.type() == null || spec.type().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Comando con 'type' mancante"));
            }
            CommandEntry e = new CommandEntry(deviceId, deviceType, spec.type());
            if (spec.parameters() != null) {
                e.parameters().putAll(spec.parameters());
            }
            e.parameters().putIfAbsent("password", password);
            entries.add(e);
        }

        // 2. Encoda i comandi (auto-append REBOOT se servisse)
        List<String> ascii;
        try {
            ascii = encoder.encode(entries);
        } catch (IllegalArgumentException e) {
            log.warn("Encoding fallito: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Errore di encoding: " + e.getMessage()));
        }

        // 3. Compone il payload finale via Tek822Worker (stesso flusso production)
        CommandsPacket packet = new CommandsPacket(ascii.size(), ascii.toArray(new String[0]));
        byte[] composedBytes = Tek822Worker.composeAsciiPayload(packet);
        String composedAscii = new String(composedBytes, StandardCharsets.US_ASCII);
        String composedHex   = toHex(composedBytes);

        // 4. Preview delle righe per device_commands. Il REBOOT auto-appended non
        //    è persistito (è sintetico, aggiunto dal Worker al dispatch).
        List<EncodeResponse.PersistenceRow> rows = new ArrayList<>();
        for (EncodeRequest.CommandSpec spec : request.commands()) {
            String paramsJson;
            try {
                paramsJson = mapper.writeValueAsString(
                        spec.parameters() == null ? Map.of() : spec.parameters());
            } catch (JsonProcessingException e) {
                paramsJson = "{}";
            }
            rows.add(new EncodeResponse.PersistenceRow(
                    deviceId, deviceType, spec.type(), paramsJson, DEFAULT_STATUS));
        }

        EncodeResponse response = new EncodeResponse(
                ascii,
                composedAscii,
                composedHex,
                composedBytes.length,
                rows
        );
        return ResponseEntity.ok(response);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }
}
