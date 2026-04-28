package com.aton.proj.gastelemetry.decoder.alarm;

import com.aton.proj.gastelemetry.common.DecoderContext;

/**
 * Helper interno per leggere soglie numeriche dalla configurazione device/global
 * con fallback su default hardcoded.
 *
 * <p>{@link DecoderContext#getConfig(String, String)} restituisce {@code Object}
 * (Integer/Double/Boolean/String) — qui normalizziamo a {@code double} per le
 * soglie numeriche.
 */
final class ThresholdRules {

    private ThresholdRules() {}

    static double doubleConfig(DecoderContext ctx, String deviceId, String key, double defaultValue) {
        Object raw = ctx.getConfig(deviceId, key);
        if (raw == null) return defaultValue;
        if (raw instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(raw.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
