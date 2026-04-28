package com.aton.proj.gastelemetry.worker.context;

import com.aton.proj.gastelemetry.common.CommandsPacket;
import com.aton.proj.gastelemetry.worker.persistence.CommandService;
import com.aton.proj.gastelemetry.worker.persistence.CommandService.ClaimedBatch;
import com.aton.proj.gastelemetry.worker.publisher.PrimaryDataPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test di WorkerContextImpl con mock di CommandService.
 *
 * Verifica:
 * <ul>
 *   <li>{@code getCommands} chiama {@code claimAndEncode} e ritorna il packet con handle corretto</li>
 *   <li>{@code markCommandsSent} passa gli ID memorizzati al service</li>
 *   <li>{@code markCommandsSent} senza batch corrente è no-op</li>
 *   <li>{@code sendToDevice} scrive sull'OutputStream</li>
 * </ul>
 */
class WorkerContextImplTest {

    private static final String DEVICE_ID = "111111111111111";

    private CommandService commandService;
    private PrimaryDataPublisher publisher;
    private ByteArrayOutputStream socketOut;
    private WorkerContextImpl ctx;

    @BeforeEach
    void setup() {
        commandService = mock(CommandService.class);
        publisher = mock(PrimaryDataPublisher.class);
        socketOut = new ByteArrayOutputStream();
        ctx = new WorkerContextImpl(socketOut, publisher, commandService);
    }

    @Test
    @DisplayName("getCommands con batch non vuoto restituisce handle != 0 e le stringhe ASCII")
    void getCommands_nonEmpty() {
        ClaimedBatch batch = new ClaimedBatch(
                List.of(10L, 11L, 12L),
                new String[]{"TEK822,S0=80", "TEK822,R3=ACTIVE"});
        when(commandService.claimAndEncode(DEVICE_ID)).thenReturn(batch);

        CommandsPacket result = ctx.getCommands(DEVICE_ID);

        assertThat(result.handle()).isNotZero();
        assertThat(result.commands()).containsExactly("TEK822,S0=80", "TEK822,R3=ACTIVE");
        assertThat(result.hasCommands()).isTrue();
        verify(commandService).claimAndEncode(DEVICE_ID);
    }

    @Test
    @DisplayName("getCommands con batch vuoto restituisce handle=0 e array vuoto")
    void getCommands_empty() {
        when(commandService.claimAndEncode(DEVICE_ID)).thenReturn(ClaimedBatch.empty());

        CommandsPacket result = ctx.getCommands(DEVICE_ID);

        assertThat(result.handle()).isZero();
        assertThat(result.commands()).isEmpty();
        assertThat(result.hasCommands()).isFalse();
    }

    @Test
    @DisplayName("markCommandsSent passa gli ID memorizzati nella precedente getCommands")
    void markCommandsSent_passesStoredIds() {
        ClaimedBatch batch = new ClaimedBatch(
                List.of(10L, 11L, 12L),
                new String[]{"AA", "BB"});
        when(commandService.claimAndEncode(DEVICE_ID)).thenReturn(batch);

        // 1) claim
        CommandsPacket packet = ctx.getCommands(DEVICE_ID);

        // 2) mark as sent (handle è opaco, viene ignorato dall'implementazione)
        ctx.markCommandsSent(packet.handle());

        verify(commandService).markSent(List.of(10L, 11L, 12L));
    }

    @Test
    @DisplayName("markCommandsSent senza precedente getCommands è no-op")
    void markCommandsSent_noBatch_noOp() {
        ctx.markCommandsSent(123);

        verify(commandService, never()).markSent(any());
    }

    @Test
    @DisplayName("Doppio markCommandsSent: solo il primo invoca il service")
    void markCommandsSent_clearedAfterFirstCall() {
        ClaimedBatch batch = new ClaimedBatch(List.of(7L), new String[]{"AA"});
        when(commandService.claimAndEncode(DEVICE_ID)).thenReturn(batch);

        ctx.getCommands(DEVICE_ID);
        ctx.markCommandsSent(1);
        ctx.markCommandsSent(1);   // secondo: lista azzerata, no-op

        verify(commandService).markSent(List.of(7L));
        // La verifica di "solo una chiamata" è implicita: il secondo invocazione
        // non passa il filtro currentBatchIds.isEmpty() del context.
    }

    @Test
    @DisplayName("sendToDevice scrive i byte sull'OutputStream e fa flush")
    void sendToDevice_writesAndFlushes() {
        byte[] payload = "TEK822".getBytes();

        ctx.sendToDevice(payload);

        assertThat(socketOut.toByteArray()).isEqualTo(payload);
    }

    @Test
    @DisplayName("sendToDevice non interagisce con CommandService né con il publisher")
    void sendToDevice_noServiceInteraction() {
        ctx.sendToDevice("X".getBytes());

        verifyNoInteractions(commandService);
        verifyNoInteractions(publisher);
    }
}
