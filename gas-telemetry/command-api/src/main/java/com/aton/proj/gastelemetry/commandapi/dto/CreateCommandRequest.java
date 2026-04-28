package com.aton.proj.gastelemetry.commandapi.dto;

import java.util.Map;

/**
 * Body JSON per {@code POST /api/commands}.
 *
 * I {@code parameters} verranno serializzati come JSON in
 * {@code device_commands.command_params}. Esempio:
 * <pre>
 * {
 *   "deviceId":   "111111111111111",
 *   "deviceType": "TEK822V1",
 *   "commandType": "SET_INTERVAL",
 *   "parameters": { "interval": 4, "samplingPeriod": 1 }
 * }
 * </pre>
 */
public record CreateCommandRequest(
        String deviceId,
        String deviceType,
        String commandType,
        Map<String, Object> parameters
) {}
