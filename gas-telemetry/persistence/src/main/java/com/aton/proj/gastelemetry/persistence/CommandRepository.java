package com.aton.proj.gastelemetry.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository per {@link CommandEntity}.
 *
 * <p>Le operazioni di transizione di stato sono fornite come {@code @Modifying @Query}
 * JPQL: vanno eseguite all'interno di una transazione (gestita dal chiamante,
 * tipicamente {@code CommandService} nel worker).
 *
 * <p>Pattern di utilizzo per il dispatch dei comandi:
 * <ol>
 *   <li>{@link #claimPending(String, Instant)} — claim atomico PENDING → IN_PROGRESS per il device.
 *       Restituisce il numero di righe aggiornate. Se 0, niente da inviare.</li>
 *   <li>{@link #findByDeviceIdAndStatusOrderByCreatedAtAsc} con {@code IN_PROGRESS}
 *       per recuperare le entity appena claim-ate.</li>
 *   <li>Encode e invio sul socket.</li>
 *   <li>{@link #markBatchAsSent(List, Instant)} con gli id appena trasmessi → IN_PROGRESS → SENT.</li>
 * </ol>
 */
@Repository
public interface CommandRepository extends JpaRepository<CommandEntity, Long> {

    /**
     * Claim atomico: aggiorna a IN_PROGRESS tutti i comandi PENDING del device specificato.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CommandEntity c " +
           "SET c.status = com.aton.proj.gastelemetry.persistence.CommandStatus.IN_PROGRESS, " +
           "    c.updatedAt = :now " +
           "WHERE c.deviceId = :deviceId " +
           "  AND c.status = com.aton.proj.gastelemetry.persistence.CommandStatus.PENDING")
    int claimPending(@Param("deviceId") String deviceId, @Param("now") Instant now);

    List<CommandEntity> findByDeviceIdAndStatusOrderByCreatedAtAsc(
            String deviceId, CommandStatus status);

    /** Marca i comandi specificati come SENT (solo se ancora IN_PROGRESS). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CommandEntity c " +
           "SET c.status = com.aton.proj.gastelemetry.persistence.CommandStatus.SENT, " +
           "    c.sentAt = :now, " +
           "    c.updatedAt = :now " +
           "WHERE c.id IN :ids " +
           "  AND c.status = com.aton.proj.gastelemetry.persistence.CommandStatus.IN_PROGRESS")
    int markBatchAsSent(@Param("ids") List<Long> ids, @Param("now") Instant now);

    /** Marca i comandi specificati come FAILED, registrando un messaggio di errore. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CommandEntity c " +
           "SET c.status = com.aton.proj.gastelemetry.persistence.CommandStatus.FAILED, " +
           "    c.errorMessage = :err, " +
           "    c.updatedAt = :now " +
           "WHERE c.id IN :ids")
    int markBatchAsFailed(@Param("ids") List<Long> ids, @Param("err") String err, @Param("now") Instant now);
}
