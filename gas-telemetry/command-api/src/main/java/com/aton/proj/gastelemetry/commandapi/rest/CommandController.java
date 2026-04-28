package com.aton.proj.gastelemetry.commandapi.rest;

import com.aton.proj.gastelemetry.commandapi.dto.CommandView;
import com.aton.proj.gastelemetry.commandapi.dto.CreateCommandRequest;
import com.aton.proj.gastelemetry.persistence.CommandEntity;
import com.aton.proj.gastelemetry.persistence.CommandRepository;
import com.aton.proj.gastelemetry.persistence.CommandStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Endpoint per creare e consultare i comandi pendenti per i device.
 *
 * <ul>
 *   <li>{@code POST /api/commands} — crea un nuovo comando in PENDING</li>
 *   <li>{@code GET  /api/commands/{id}} — leggi singolo comando</li>
 *   <li>{@code GET  /api/devices/{deviceId}/commands?status=...} — lista per device</li>
 * </ul>
 *
 * <p>L'invio fisico al device è compito del worker (modulo separato): qui ci
 * limitiamo a inserire la riga PENDING. Il worker la prenderà alla prossima
 * connessione TCP del device.
 */
@RestController
@RequestMapping("/api")
public class CommandController {

    private final CommandRepository repo;
    private final ObjectMapper mapper;

    public CommandController(CommandRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @PostMapping("/commands")
    public ResponseEntity<CommandView> createCommand(@RequestBody CreateCommandRequest req) {
        if (req.deviceId() == null || req.commandType() == null || req.deviceType() == null) {
            return ResponseEntity.badRequest().build();
        }
        CommandEntity e = new CommandEntity();
        e.setDeviceId(req.deviceId());
        e.setDeviceType(req.deviceType());
        e.setCommandType(req.commandType());
        e.setStatus(CommandStatus.PENDING);
        Map<String, Object> params = req.parameters() != null ? req.parameters() : Map.of();
        try {
            e.setCommandParams(mapper.writeValueAsString(params));
        } catch (JsonProcessingException ex) {
            // Mappa malformata sarebbe sorprendente da Jackson — 400 Bad Request
            return ResponseEntity.badRequest().build();
        }
        CommandEntity saved = repo.save(e);
        return ResponseEntity
                .created(URI.create("/api/commands/" + saved.getId()))
                .body(CommandView.from(saved));
    }

    @GetMapping("/commands/{id}")
    public ResponseEntity<CommandView> getCommand(@PathVariable Long id) {
        return repo.findById(id)
                .map(CommandView::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Lista i comandi di un device, filtrabili per status.
     * Senza filtro restituisce solo PENDING (il caso d'uso più frequente per UI).
     */
    @GetMapping("/devices/{deviceId}/commands")
    public List<CommandView> listForDevice(
            @PathVariable String deviceId,
            @RequestParam(required = false, defaultValue = "PENDING") CommandStatus status) {
        return repo.findByDeviceIdAndStatusOrderByCreatedAtAsc(deviceId, status)
                .stream()
                .map(CommandView::from)
                .toList();
    }
}
