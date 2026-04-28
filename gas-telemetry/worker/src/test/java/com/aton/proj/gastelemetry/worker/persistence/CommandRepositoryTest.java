package com.aton.proj.gastelemetry.worker.persistence;

import com.aton.proj.gastelemetry.persistence.CommandEntity;
import com.aton.proj.gastelemetry.persistence.CommandRepository;
import com.aton.proj.gastelemetry.persistence.CommandStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test del CommandRepository con H2 in-memory.
 *
 * Verifica:
 * <ul>
 *   <li>{@code claimPending} aggiorna esattamente le righe PENDING del device target</li>
 *   <li>L'atomicità a livello SQL (UPDATE singolo): le righe di altri device non vengono toccate</li>
 *   <li>{@code markBatchAsSent} agisce solo su righe IN_PROGRESS</li>
 *   <li>{@code markBatchAsFailed} non ha vincolo di stato corrente</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase
class CommandRepositoryTest {

    private static final String DEVICE_A = "111111111111111";
    private static final String DEVICE_B = "222222222222222";

    @Autowired
    private CommandRepository repo;

    // Ogni test in @DataJpaTest gira in una transazione rollback-ata a fine test:
    // non serve cleanup esplicito.

    // ================================================================
    //  claimPending
    // ================================================================

    @Test
    @DisplayName("claimPending aggiorna SOLO i comandi PENDING del device target")
    void claimPending_updatesOnlyTargetDevicePending() {
        // 3 PENDING per A, 2 PENDING per B, 1 SENT per A (non deve essere toccato)
        savePending(DEVICE_A, "REQUEST_STATUS");
        savePending(DEVICE_A, "REBOOT");
        savePending(DEVICE_A, "REQUEST_GPS");
        savePending(DEVICE_B, "REQUEST_STATUS");
        savePending(DEVICE_B, "REBOOT");
        saveWithStatus(DEVICE_A, "SHUTDOWN", CommandStatus.SENT);

        int claimed = repo.claimPending(DEVICE_A, Instant.now());

        assertThat(claimed).isEqualTo(3);

        List<CommandEntity> aInProgress = repo.findByDeviceIdAndStatusOrderByCreatedAtAsc(
                DEVICE_A, CommandStatus.IN_PROGRESS);
        List<CommandEntity> bPending = repo.findByDeviceIdAndStatusOrderByCreatedAtAsc(
                DEVICE_B, CommandStatus.PENDING);
        List<CommandEntity> aSent = repo.findByDeviceIdAndStatusOrderByCreatedAtAsc(
                DEVICE_A, CommandStatus.SENT);

        assertThat(aInProgress).hasSize(3);
        assertThat(bPending).hasSize(2);              // device B intatto
        assertThat(aSent).hasSize(1);                 // SENT preesistente non riportato in IN_PROGRESS
    }

    @Test
    @DisplayName("claimPending è idempotente quando non ci sono righe PENDING")
    void claimPending_returnsZeroWhenNothingToClaim() {
        saveWithStatus(DEVICE_A, "REBOOT", CommandStatus.SENT);

        int claimed = repo.claimPending(DEVICE_A, Instant.now());

        assertThat(claimed).isZero();
        assertThat(repo.findByDeviceIdAndStatusOrderByCreatedAtAsc(DEVICE_A, CommandStatus.IN_PROGRESS))
                .isEmpty();
    }

    @Test
    @DisplayName("claimPending mantiene l'ordine cronologico di createdAt nelle row aggiornate")
    void claimPending_preservesChronologicalOrder() {
        Instant t0 = Instant.parse("2024-01-01T10:00:00Z");
        saveWithCreatedAt(DEVICE_A, "REBOOT",          t0);
        saveWithCreatedAt(DEVICE_A, "REQUEST_STATUS",  t0.plusSeconds(10));
        saveWithCreatedAt(DEVICE_A, "REQUEST_GPS",     t0.plusSeconds(20));

        repo.claimPending(DEVICE_A, Instant.now());

        List<CommandEntity> rows = repo.findByDeviceIdAndStatusOrderByCreatedAtAsc(
                DEVICE_A, CommandStatus.IN_PROGRESS);

        assertThat(rows).extracting(CommandEntity::getCommandType)
                .containsExactly("REBOOT", "REQUEST_STATUS", "REQUEST_GPS");
    }

    // ================================================================
    //  markBatchAsSent
    // ================================================================

    @Test
    @DisplayName("markBatchAsSent aggiorna solo righe IN_PROGRESS")
    void markBatchAsSent_onlyAffectsInProgress() {
        Long inProgressId = saveWithStatus(DEVICE_A, "REBOOT",     CommandStatus.IN_PROGRESS);
        Long pendingId    = saveWithStatus(DEVICE_A, "REQUEST_STATUS", CommandStatus.PENDING);

        int updated = repo.markBatchAsSent(List.of(inProgressId, pendingId), Instant.now());

        assertThat(updated).isEqualTo(1);
        assertThat(repo.findById(inProgressId)).get()
                .satisfies(e -> {
                    assertThat(e.getStatus()).isEqualTo(CommandStatus.SENT);
                    assertThat(e.getSentAt()).isNotNull();
                });
        assertThat(repo.findById(pendingId)).get()
                .satisfies(e -> {
                    assertThat(e.getStatus()).isEqualTo(CommandStatus.PENDING);
                    assertThat(e.getSentAt()).isNull();
                });
    }

    @Test
    @DisplayName("markBatchAsSent su lista vuota non fa nulla")
    void markBatchAsSent_emptyList_noOp() {
        Long id = saveWithStatus(DEVICE_A, "REBOOT", CommandStatus.IN_PROGRESS);

        int updated = repo.markBatchAsSent(List.of(), Instant.now());

        assertThat(updated).isZero();
        assertThat(repo.findById(id)).get()
                .extracting(CommandEntity::getStatus)
                .isEqualTo(CommandStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("markBatchAsSent è idempotente su righe già SENT (nessuna transizione)")
    void markBatchAsSent_alreadySent_noOp() {
        Long id = saveWithStatus(DEVICE_A, "REBOOT", CommandStatus.SENT);

        int updated = repo.markBatchAsSent(List.of(id), Instant.now());

        assertThat(updated).isZero();
    }

    // ================================================================
    //  markBatchAsFailed
    // ================================================================

    @Test
    @DisplayName("markBatchAsFailed registra error_message e cambia stato")
    void markBatchAsFailed_recordsError() {
        Long id = saveWithStatus(DEVICE_A, "REBOOT", CommandStatus.IN_PROGRESS);

        int updated = repo.markBatchAsFailed(List.of(id), "device unreachable", Instant.now());

        assertThat(updated).isEqualTo(1);
        assertThat(repo.findById(id)).get()
                .satisfies(e -> {
                    assertThat(e.getStatus()).isEqualTo(CommandStatus.FAILED);
                    assertThat(e.getErrorMessage()).isEqualTo("device unreachable");
                });
    }

    // ================================================================
    //  Flusso completo end-to-end
    // ================================================================

    @Test
    @DisplayName("Flusso completo: PENDING → claimPending → IN_PROGRESS → markBatchAsSent → SENT")
    void fullFlow() {
        savePending(DEVICE_A, "SET_INTERVAL");
        savePending(DEVICE_A, "REBOOT");

        int claimed = repo.claimPending(DEVICE_A, Instant.now());
        assertThat(claimed).isEqualTo(2);

        List<CommandEntity> inProgress = repo.findByDeviceIdAndStatusOrderByCreatedAtAsc(
                DEVICE_A, CommandStatus.IN_PROGRESS);
        List<Long> ids = inProgress.stream().map(CommandEntity::getId).toList();

        int sent = repo.markBatchAsSent(ids, Instant.now());
        assertThat(sent).isEqualTo(2);

        // Seconda chiamata claimPending: niente da claim-are
        assertThat(repo.claimPending(DEVICE_A, Instant.now())).isZero();

        // Tutti i comandi sono SENT
        assertThat(repo.findByDeviceIdAndStatusOrderByCreatedAtAsc(DEVICE_A, CommandStatus.SENT))
                .hasSize(2)
                .allSatisfy(e -> assertThat(e.getSentAt()).isNotNull());
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private Long savePending(String deviceId, String commandType) {
        return saveWithStatus(deviceId, commandType, CommandStatus.PENDING);
    }

    private Long saveWithStatus(String deviceId, String commandType, CommandStatus status) {
        CommandEntity e = new CommandEntity();
        e.setDeviceId(deviceId);
        e.setDeviceType("TEK822V1");
        e.setCommandType(commandType);
        e.setStatus(status);
        return repo.saveAndFlush(e).getId();
    }

    private Long saveWithCreatedAt(String deviceId, String commandType, Instant createdAt) {
        CommandEntity e = new CommandEntity();
        e.setDeviceId(deviceId);
        e.setDeviceType("TEK822V1");
        e.setCommandType(commandType);
        e.setStatus(CommandStatus.PENDING);
        e.setCreatedAt(createdAt);
        return repo.saveAndFlush(e).getId();
    }
}
