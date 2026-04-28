package com.aton.proj.gastelemetry.worker.context;

import com.aton.proj.gastelemetry.common.CommandsPacket;
import com.aton.proj.gastelemetry.common.WorkerContext;
import com.aton.proj.gastelemetry.worker.persistence.CommandService;
import com.aton.proj.gastelemetry.worker.persistence.CommandService.ClaimedBatch;
import com.aton.proj.gastelemetry.worker.publisher.PrimaryDataPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Implementazione di WorkerContext per una singola sessione TCP.
 *
 * È un oggetto per-connessione (non un bean Spring): viene creato da {@code TcpServer}
 * per ogni socket accettato, con l'{@code OutputStream} e il {@code CommandService}
 * specifici. Lo stato dei comandi claim-ati per la sessione vive in {@link #currentBatchIds}
 * — la sua durata coincide con la durata della connessione.
 *
 * <p>Il flusso atteso da {@code Tek822Worker.doWork()} è:
 * <pre>
 *   ctx.publishPrimaryData(...)
 *   CommandsPacket cmds = ctx.getCommands(deviceId);
 *   if (cmds.hasCommands()) {
 *       ctx.sendToDevice( cmds → bytes );
 *       ctx.markCommandsSent(cmds.handle());
 *   }
 * </pre>
 *
 * <p><b>Rischio crash</b>: se il worker termina anormalmente tra
 * {@link #sendToDevice(byte[])} e {@link #markCommandsSent(int)}, i comandi restano
 * IN_PROGRESS sul DB. TODO: schedulare un job di reset (~5 min, basato su {@code updated_at}).
 */
public class WorkerContextImpl implements WorkerContext {

    private static final Logger log = LoggerFactory.getLogger(WorkerContextImpl.class);

    /** Handle sentinella usato quando ci sono comandi da inviare (il valore è opaco). */
    private static final int HANDLE_HAS_COMMANDS = 1;
    private static final int HANDLE_NONE         = 0;

    private final OutputStream socketOut;
    private final PrimaryDataPublisher publisher;
    private final CommandService commandService;

    /**
     * Id dei comandi claim-ati nell'ultima invocazione di {@link #getCommands(String)}.
     * Vita: dalla {@code getCommands} fino alla {@code markCommandsSent} successiva (o fine connessione).
     */
    private List<Long> currentBatchIds = List.of();

    public WorkerContextImpl(OutputStream socketOut,
                             PrimaryDataPublisher publisher,
                             CommandService commandService) {
        this.socketOut = socketOut;
        this.publisher = publisher;
        this.commandService = commandService;
    }

    @Override
    public void publishPrimaryData(String deviceId, byte[] data) {
        // Delega la pubblicazione al PrimaryDataPublisher iniettato (Event Hub reale o no-op).
        publisher.publish(deviceId, data);
    }

    @Override
    public CommandsPacket getCommands(String deviceId) {
        ClaimedBatch batch = commandService.claimAndEncode(deviceId);
        currentBatchIds = batch.ids();
        if (batch.isEmpty()) {
            return new CommandsPacket(HANDLE_NONE, new String[0]);
        }
        return new CommandsPacket(HANDLE_HAS_COMMANDS, batch.encoded());
    }

    /**
     * Marca come SENT i comandi claim-ati nell'ultima {@link #getCommands(String)}.
     *
     * <p>Il parametro {@code handle} è un'identificativo opaco: la lista degli ID
     * effettivi vive in {@link #currentBatchIds}. Questo design semplifica il marker
     * (non serve passare gli ID nel {@link CommandsPacket}, vincolato a {@code int handle})
     * e sfrutta il fatto che {@code WorkerContextImpl} è per-connessione.
     */
    @Override
    public void markCommandsSent(int handle) {
        if (currentBatchIds.isEmpty()) {
            log.debug("markCommandsSent({}) chiamato senza batch corrente — no-op", handle);
            return;
        }
        commandService.markSent(currentBatchIds);
        log.debug("Batch di {} comando/i marcato SENT (handle={})", currentBatchIds.size(), handle);
        currentBatchIds = List.of();
    }

    @Override
    public void sendToDevice(byte[] data) {
        try {
            socketOut.write(data);
            socketOut.flush();
        } catch (IOException e) {
            log.error("Failed to send data to device", e);
            throw new RuntimeException("sendToDevice failed", e);
        }
    }
}
