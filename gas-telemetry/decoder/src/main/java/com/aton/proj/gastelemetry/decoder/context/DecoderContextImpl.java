package com.aton.proj.gastelemetry.decoder.context;

import com.aton.proj.gastelemetry.common.Alarm;
import com.aton.proj.gastelemetry.common.DecodedPacket;
import com.aton.proj.gastelemetry.common.DecoderContext;
import com.aton.proj.gastelemetry.decoder.alarm.AlarmRule;
import com.aton.proj.gastelemetry.decoder.persistence.TimeSeriesService;
import com.aton.proj.gastelemetry.persistence.ConfigEntity;
import com.aton.proj.gastelemetry.persistence.ConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementazione di DecoderContext.
 *
 * A differenza di WorkerContextImpl (per-connessione), questo è un bean Spring
 * singleton: il Decoder non ha stato per-sessione, quindi può essere condiviso.
 *
 * <p><b>getConfig:</b> legge da {@link ConfigRepository} (tabella {@code device_config},
 * key-value). Risoluzione tipi a runtime — vedi {@link #parseValue(String)}.
 * Il fallback è: per {@link #getConfig(String, String)} si cerca prima
 * {@code (deviceId, key)}, poi si fa fallback su global ({@code (*, key)}).
 */
@Component
public class DecoderContextImpl implements DecoderContext {

    private static final Logger log = LoggerFactory.getLogger(DecoderContextImpl.class);

    private final ConfigRepository configRepository;
    private final TimeSeriesService timeSeriesService;
    private final List<AlarmRule> alarmRules;

    public DecoderContextImpl(ConfigRepository configRepository,
                              TimeSeriesService timeSeriesService,
                              List<AlarmRule> alarmRules) {
        this.configRepository  = configRepository;
        this.timeSeriesService = timeSeriesService;
        this.alarmRules        = alarmRules;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String cfgKey) {
        return (T) configRepository.findGlobal(cfgKey)
                .map(ConfigEntity::getConfigValue)
                .map(DecoderContextImpl::parseValue)
                .orElse(null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String deviceId, String cfgKey) {
        Optional<String> deviceValue = configRepository
                .findForDevice(deviceId, cfgKey)
                .map(ConfigEntity::getConfigValue);
        if (deviceValue.isPresent()) {
            return (T) parseValue(deviceValue.get());
        }
        // Fallback su configurazione globale
        return getConfig(cfgKey);
    }

    @Override
    public void publishDecodedData(String deviceId, DecodedPacket packet) {
        // Applica le regole di allarme sulle misure ricevute, accodando i nuovi
        // allarmi a quelli già presenti nel pacchetto (es. allarmi device-side).
        DecodedPacket enriched = enrichWithRuleAlarms(deviceId, packet);

        log.debug("Publishing {} measures and {} alarms (of which {} from rules) for device {}",
                enriched.measures().size(), enriched.alarms().size(),
                enriched.alarms().size() - packet.alarms().size(), deviceId);
        timeSeriesService.persist(deviceId, enriched);
    }

    /**
     * Esegue tutte le {@link AlarmRule} sul batch di misure e produce un
     * nuovo {@link DecodedPacket} con gli allarmi originali + quelli delle regole.
     * Le regole non possono modificare le misure: vedono solo una vista sola lettura.
     */
    DecodedPacket enrichWithRuleAlarms(String deviceId, DecodedPacket packet) {
        if (alarmRules.isEmpty() || packet.measures().isEmpty()) return packet;

        List<Alarm> merged = new ArrayList<>(packet.alarms());
        for (AlarmRule rule : alarmRules) {
            merged.addAll(rule.evaluate(deviceId, packet.measures(), this));
        }
        return new DecodedPacket(packet.measures(), merged);
    }

    /**
     * Parsing best-effort del valore stringa: prova int → double → boolean → string.
     * I tipi più ricchi (es. liste, JSON) andranno introdotti quando servono — per
     * ora le chiavi note (soglie allarmi, timeout) sono tutte numeriche o booleane.
     */
    static Object parseValue(String raw) {
        if (raw == null) return null;
        // Boolean
        if ("true".equalsIgnoreCase(raw))  return Boolean.TRUE;
        if ("false".equalsIgnoreCase(raw)) return Boolean.FALSE;
        // Numeric
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) { /* fall through */ }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) { /* fall through */ }
        // String
        return raw;
    }
}
