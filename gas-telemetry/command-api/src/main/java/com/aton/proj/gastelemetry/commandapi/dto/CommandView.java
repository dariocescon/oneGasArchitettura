package com.aton.proj.gastelemetry.commandapi.dto;

import com.aton.proj.gastelemetry.persistence.CommandEntity;
import com.aton.proj.gastelemetry.persistence.CommandStatus;

import java.time.Instant;

/**
 * View JSON di {@link CommandEntity} per le risposte HTTP.
 * Esclude {@code command_params} grezzo (JSON serializzato): se serve nelle
 * risposte si può estendere in futuro deserializzandolo lato controller.
 */
public record CommandView(
        Long id,
        String deviceId,
        String deviceType,
        String commandType,
        CommandStatus status,
        Instant createdAt,
        Instant sentAt,
        Instant updatedAt,
        Integer retryCount,
        Integer maxRetries,
        String errorMessage
) {
    public static CommandView from(CommandEntity e) {
        return new CommandView(
                e.getId(), e.getDeviceId(), e.getDeviceType(), e.getCommandType(),
                e.getStatus(), e.getCreatedAt(), e.getSentAt(), e.getUpdatedAt(),
                e.getRetryCount(), e.getMaxRetries(), e.getErrorMessage());
    }
}
