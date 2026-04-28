package com.aton.proj.gastelemetry.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Entity JPA che rappresenta un comando da inviare al device.
 *
 * Il ciclo di vita è governato dall'enum {@link CommandStatus}.
 *
 * Tabella: {@code device_commands}, indici:
 * <ul>
 *   <li>{@code (device_id, status)} — query principale: claim e select per device</li>
 *   <li>{@code created_at} — ordinamento e cleanup</li>
 * </ul>
 */
@Entity
@Table(
        name = "device_commands",
        indexes = {
                @Index(name = "ix_device_commands_device_status", columnList = "device_id, status"),
                @Index(name = "ix_device_commands_created_at",     columnList = "created_at")
        }
)
public class CommandEntity {

    private static final TypeReference<Map<String, Object>> PARAMS_TYPE = new TypeReference<>() {};

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 50)
    private String deviceId;

    @Column(name = "device_type", nullable = false, length = 50)
    private String deviceType;

    @Column(name = "command_type", nullable = false, length = 50)
    private String commandType;

    /** JSON serializzato di {@code Map<String,Object>} con i parametri del comando. */
    @Column(name = "command_params", columnDefinition = "TEXT")
    private String commandParams;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CommandStatus status = CommandStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 3;

    // ---- Lifecycle hooks ----

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = CommandStatus.PENDING;
        if (retryCount == null) retryCount = 0;
        if (maxRetries == null) maxRetries = 3;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    // ---- JSON helper per command_params ----

    /**
     * Deserializza {@link #commandParams} (JSON) in una mappa.
     * @return mappa vuota se {@code commandParams} è null/blank
     */
    public Map<String, Object> parseParams(ObjectMapper mapper) {
        if (commandParams == null || commandParams.isBlank()) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> params = mapper.readValue(commandParams, PARAMS_TYPE);
            return params != null ? params : new HashMap<>();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "command_params malformato per CommandEntity id=" + id, e);
        }
    }

    // ---- Getters / setters ----

    public Long getId()                  { return id; }
    public void setId(Long id)           { this.id = id; }

    public String getDeviceId()                   { return deviceId; }
    public void setDeviceId(String deviceId)      { this.deviceId = deviceId; }

    public String getDeviceType()                 { return deviceType; }
    public void setDeviceType(String deviceType)  { this.deviceType = deviceType; }

    public String getCommandType()                { return commandType; }
    public void setCommandType(String commandType){ this.commandType = commandType; }

    public String getCommandParams()              { return commandParams; }
    public void setCommandParams(String params)   { this.commandParams = params; }

    public CommandStatus getStatus()              { return status; }
    public void setStatus(CommandStatus status)   { this.status = status; }

    public Instant getCreatedAt()                 { return createdAt; }
    public void setCreatedAt(Instant createdAt)   { this.createdAt = createdAt; }

    public Instant getSentAt()                    { return sentAt; }
    public void setSentAt(Instant sentAt)         { this.sentAt = sentAt; }

    public Instant getUpdatedAt()                 { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt)   { this.updatedAt = updatedAt; }

    public String getErrorMessage()               { return errorMessage; }
    public void setErrorMessage(String error)     { this.errorMessage = error; }

    public Integer getRetryCount()                { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public Integer getMaxRetries()                { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
}
