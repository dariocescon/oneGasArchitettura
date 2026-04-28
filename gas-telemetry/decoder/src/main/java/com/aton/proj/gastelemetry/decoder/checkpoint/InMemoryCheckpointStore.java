package com.aton.proj.gastelemetry.decoder.checkpoint;

import com.azure.messaging.eventhubs.CheckpointStore;
import com.azure.messaging.eventhubs.models.Checkpoint;
import com.azure.messaging.eventhubs.models.PartitionOwnership;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementazione "minima" di {@link CheckpointStore} che mantiene ownership e
 * checkpoint solo in memoria.
 *
 * <p><b>Limiti</b>:
 * <ul>
 *   <li>Non sopravvive a restart: dopo un riavvio del Decoder gli eventi vengono
 *       riprocessati dall'inizio della partizione.</li>
 *   <li>Non condivisibile tra istanze: con N decoder in scale-out questa impl
 *       NON garantisce che ogni partizione sia servita da una sola istanza.</li>
 * </ul>
 *
 * <p>Serve esclusivamente come placeholder finché il task <i>Checkpoint store</i>
 * della roadmap (oggi in stand-by) non viene completato sostituendola con
 * {@code BlobCheckpointStore} (azure-messaging-eventhubs-checkpointstore-blob),
 * che usa Azure Blob Storage come backing store distribuito.
 *
 * <p>Funziona per:
 * <ul>
 *   <li>sviluppo locale (single instance, restart frequenti accettabili)</li>
 *   <li>test integrati con un Event Hub di test</li>
 * </ul>
 *
 * Thread-safe: usa {@link ConcurrentHashMap}.
 */
public class InMemoryCheckpointStore implements CheckpointStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryCheckpointStore.class);

    private final Map<String, PartitionOwnership> ownershipMap   = new ConcurrentHashMap<>();
    private final Map<String, Checkpoint>          checkpointMap = new ConcurrentHashMap<>();

    @Override
    public Flux<PartitionOwnership> listOwnership(String fullyQualifiedNamespace,
                                                  String eventHubName,
                                                  String consumerGroup) {
        String prefix = scopePrefix(fullyQualifiedNamespace, eventHubName, consumerGroup);
        return Flux.fromIterable(ownershipMap.entrySet())
                .filter(e -> e.getKey().startsWith(prefix))
                .map(Map.Entry::getValue);
    }

    @Override
    public Flux<PartitionOwnership> claimOwnership(List<PartitionOwnership> requestedPartitionOwnerships) {
        return Flux.fromIterable(requestedPartitionOwnerships)
                .map(req -> {
                    req.setLastModifiedTime(System.currentTimeMillis());
                    req.setETag(UUID.randomUUID().toString());
                    ownershipMap.put(ownershipKey(req), req);
                    return req;
                })
                .doOnNext(o -> log.debug("Claim ownership partition={} owner={}",
                        o.getPartitionId(), o.getOwnerId()));
    }

    @Override
    public Flux<Checkpoint> listCheckpoints(String fullyQualifiedNamespace,
                                            String eventHubName,
                                            String consumerGroup) {
        String prefix = scopePrefix(fullyQualifiedNamespace, eventHubName, consumerGroup);
        return Flux.fromIterable(checkpointMap.entrySet())
                .filter(e -> e.getKey().startsWith(prefix))
                .map(Map.Entry::getValue);
    }

    @Override
    public Mono<Void> updateCheckpoint(Checkpoint checkpoint) {
        checkpointMap.put(checkpointKey(checkpoint), checkpoint);
        log.debug("Update checkpoint partition={} offset={} seq={}",
                checkpoint.getPartitionId(), checkpoint.getOffset(), checkpoint.getSequenceNumber());
        return Mono.empty();
    }

    // ---- key building -------------------------------------------------------

    private static String scopePrefix(String fqns, String hub, String group) {
        return (fqns + "/" + hub + "/" + group + "/").toLowerCase(Locale.ROOT);
    }

    private static String ownershipKey(PartitionOwnership o) {
        return scopePrefix(o.getFullyQualifiedNamespace(), o.getEventHubName(), o.getConsumerGroup())
                + o.getPartitionId();
    }

    private static String checkpointKey(Checkpoint c) {
        return scopePrefix(c.getFullyQualifiedNamespace(), c.getEventHubName(), c.getConsumerGroup())
                + c.getPartitionId();
    }
}
