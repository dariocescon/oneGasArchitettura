package com.aton.proj.gastelemetry.worker.persistence;

import com.aton.proj.gastelemetry.persistence.CommandEntity;
import com.aton.proj.gastelemetry.persistence.CommandRepository;
import com.aton.proj.gastelemetry.persistence.CommandStatus;
import com.aton.proj.gastelemetry.worker.impl.CommandEntry;
import com.aton.proj.gastelemetry.worker.impl.Tek822Encoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Servizio Spring che orchestra il ciclo di vita dei comandi durante il flusso TCP del Worker.
 *
 * Centralizza la transazionalità (`@Transactional`) e l'integrazione tra
 * {@link CommandRepository}, {@link Tek822Encoder} e {@link ObjectMapper} (JSON).
 *
 * Viene iniettato in {@code WorkerContextImpl} (oggetto per-connessione, non bean Spring)
 * tramite {@code TcpServer}, che è il bean Spring che istanzia i context per ogni socket.
 *
 * Pattern d'uso:
 * <pre>
 *   ClaimedBatch batch = commandService.claimAndEncode(deviceId);
 *   if (batch.encoded().length > 0) {
 *       socket.write(batch.encoded());          // invia al device
 *       commandService.markSent(batch.ids());   // aggiorna DB → SENT
 *   }
 * </pre>
 */
@Service
public class CommandService {

    private static final Logger log = LoggerFactory.getLogger(CommandService.class);

    private final CommandRepository repo;
    private final Tek822Encoder encoder;
    private final ObjectMapper mapper;

    public CommandService(CommandRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
        this.encoder = new Tek822Encoder(); // utility pura, non bean
    }

    /**
     * Risultato di {@link #claimAndEncode(String)}: gli id dei comandi claim-ati
     * (per il successivo {@link #markSent(List)}) e le stringhe <b>ASCII</b>
     * (es. {@code "TEK822,S0=80"}) prodotte dall'encoder. La concatenazione
     * finale del payload da scrivere sul socket è responsabilità di
     * {@code Tek822Worker.composeAsciiPayload()}.
     */
    public record ClaimedBatch(List<Long> ids, String[] encoded) {

        public static ClaimedBatch empty() {
            return new ClaimedBatch(List.of(), new String[0]);
        }

        public boolean isEmpty() {
            return encoded.length == 0;
        }
    }

    /**
     * Claim atomico dei comandi PENDING per il device + encoding TEK822.
     *
     * Flusso:
     * <ol>
     *   <li>UPDATE PENDING → IN_PROGRESS WHERE device_id=?</li>
     *   <li>SELECT IN_PROGRESS WHERE device_id=? ORDER BY created_at</li>
     *   <li>Mappa entity → CommandEntry (deserializzando command_params JSON)</li>
     *   <li>{@link Tek822Encoder#encode(List)} → List&lt;String&gt; ASCII (con auto-append REBOOT)</li>
     * </ol>
     *
     * @return batch claim-ato (eventualmente vuoto se non c'erano PENDING)
     */
    @Transactional
    public ClaimedBatch claimAndEncode(String deviceId) {
        Instant now = Instant.now();
        int claimed = repo.claimPending(deviceId, now);
        if (claimed == 0) {
            log.debug("Nessun comando PENDING per device {}", deviceId);
            return ClaimedBatch.empty();
        }

        List<CommandEntity> rows = repo.findByDeviceIdAndStatusOrderByCreatedAtAsc(
                deviceId, CommandStatus.IN_PROGRESS);

        List<CommandEntry> entries = rows.stream()
                .map(e -> new CommandEntry(
                        e.getId(), e.getDeviceId(), e.getDeviceType(),
                        e.getCommandType(), e.parseParams(mapper)))
                .toList();

        List<String> ascii = encoder.encode(entries);
        List<Long> ids     = rows.stream().map(CommandEntity::getId).toList();

        log.info("Claim per device {}: {} comando/i in DB → {} stringhe ASCII (incluso eventuale REBOOT)",
                deviceId, ids.size(), ascii.size());

        return new ClaimedBatch(ids, ascii.toArray(new String[0]));
    }

    /**
     * Marca i comandi specificati come SENT.
     * Idempotente su lista vuota o su comandi già non in IN_PROGRESS.
     */
    @Transactional
    public void markSent(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        int updated = repo.markBatchAsSent(ids, Instant.now());
        log.debug("markSent: {} comandi aggiornati su {} richiesti", updated, ids.size());
    }

    /**
     * Marca i comandi specificati come FAILED, registrando un messaggio di errore.
     */
    @Transactional
    public void markFailed(List<Long> ids, String errorMessage) {
        if (ids == null || ids.isEmpty()) return;
        int updated = repo.markBatchAsFailed(ids, errorMessage, Instant.now());
        log.warn("markFailed: {} comandi marcati FAILED ({})", updated, errorMessage);
    }
}
