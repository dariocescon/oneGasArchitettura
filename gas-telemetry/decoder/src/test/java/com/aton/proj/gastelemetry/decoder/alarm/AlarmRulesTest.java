package com.aton.proj.gastelemetry.decoder.alarm;

import com.aton.proj.gastelemetry.common.Alarm;
import com.aton.proj.gastelemetry.common.DecoderContext;
import com.aton.proj.gastelemetry.common.Measure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test unitari delle 4 {@link AlarmRule} sincrone.
 *
 * Verifica per ogni regola:
 *  - trigger sotto/sopra soglia
 *  - non-trigger entro soglia
 *  - default usato se config assente
 *  - override da config (mock di DecoderContext)
 *  - regole ignorano misure con obisCode diverso dal proprio
 */
class AlarmRulesTest {

    private static final String DEV = "111111111111111";
    private static final Instant T0 = Instant.parse("2026-04-28T10:00:00Z");

    private DecoderContext ctxWithoutConfig() {
        DecoderContext ctx = mock(DecoderContext.class);
        when(ctx.getConfig(anyString(), anyString())).thenReturn(null);
        return ctx;
    }

    private DecoderContext ctxWithConfig(String key, Object value) {
        DecoderContext ctx = mock(DecoderContext.class);
        when(ctx.<Object>getConfig(anyString(), anyString())).thenReturn(null);
        when(ctx.<Object>getConfig(DEV, key)).thenReturn(value);
        return ctx;
    }

    // ---- Tank low ----

    @Test
    @DisplayName("TankLevelLow: 15 < default 20 → triggera")
    void tankLow_triggersOnDefault() {
        List<Alarm> out = new TankLevelLowRule().evaluate(DEV,
                List.of(new Measure(T0, AlarmCodes.OBIS_TANK_LEVEL, 15.0, "%")),
                ctxWithoutConfig());
        assertThat(out).hasSize(1);
        assertThat(out.get(0).alarmCode()).isEqualTo(AlarmCodes.ALARM_TANK_LOW);
    }

    @Test
    @DisplayName("TankLevelLow: 25 > 20 → no allarme")
    void tankLow_noTrigger() {
        List<Alarm> out = new TankLevelLowRule().evaluate(DEV,
                List.of(new Measure(T0, AlarmCodes.OBIS_TANK_LEVEL, 25.0, "%")),
                ctxWithoutConfig());
        assertThat(out).isEmpty();
    }

    @Test
    @DisplayName("TankLevelLow: soglia configurata override-a il default")
    void tankLow_configOverride() {
        // Soglia custom 30: misura 25 ora triggera
        List<Alarm> out = new TankLevelLowRule().evaluate(DEV,
                List.of(new Measure(T0, AlarmCodes.OBIS_TANK_LEVEL, 25.0, "%")),
                ctxWithConfig(AlarmCodes.CFG_TANK_LOW_THRESHOLD, 30));
        assertThat(out).hasSize(1);
    }

    @Test
    @DisplayName("TankLevelLow ignora misure con obisCode diverso")
    void tankLow_ignoresOtherObis() {
        List<Alarm> out = new TankLevelLowRule().evaluate(DEV,
                List.of(new Measure(T0, AlarmCodes.OBIS_BATTERY_VOLTAGE, 1.0, "V")),
                ctxWithoutConfig());
        assertThat(out).isEmpty();
    }

    // ---- Tank high ----

    @Test
    @DisplayName("TankLevelHigh: 95 > 90 → triggera")
    void tankHigh_triggers() {
        List<Alarm> out = new TankLevelHighRule().evaluate(DEV,
                List.of(new Measure(T0, AlarmCodes.OBIS_TANK_LEVEL, 95.0, "%")),
                ctxWithoutConfig());
        assertThat(out).hasSize(1);
        assertThat(out.get(0).alarmCode()).isEqualTo(AlarmCodes.ALARM_TANK_HIGH);
    }

    // ---- Battery low ----

    @Test
    @DisplayName("BatteryLow: 3100mV < default 3200mV → triggera")
    void batteryLow_triggers() {
        List<Alarm> out = new BatteryLowRule().evaluate(DEV,
                List.of(new Measure(T0, AlarmCodes.OBIS_BATTERY_VOLTAGE, 3100.0, "mV")),
                ctxWithoutConfig());
        assertThat(out).hasSize(1);
        assertThat(out.get(0).alarmCode()).isEqualTo(AlarmCodes.ALARM_BATTERY_LOW);
    }

    @Test
    @DisplayName("BatteryLow: 3500mV > 3200mV → no allarme")
    void batteryLow_noTrigger() {
        List<Alarm> out = new BatteryLowRule().evaluate(DEV,
                List.of(new Measure(T0, AlarmCodes.OBIS_BATTERY_VOLTAGE, 3500.0, "mV")),
                ctxWithoutConfig());
        assertThat(out).isEmpty();
    }

    // ---- Temperature ----

    @Test
    @DisplayName("Temperature: -15°C < default min -10 → triggera")
    void tempBelowMin() {
        List<Alarm> out = new TemperatureRangeRule().evaluate(DEV,
                List.of(new Measure(T0, AlarmCodes.OBIS_TEMPERATURE, -15.0, "C")),
                ctxWithoutConfig());
        assertThat(out).hasSize(1);
        assertThat(out.get(0).alarmCode()).isEqualTo(AlarmCodes.ALARM_TEMPERATURE_OUT_RANGE);
    }

    @Test
    @DisplayName("Temperature: 70°C > default max 60 → triggera")
    void tempAboveMax() {
        List<Alarm> out = new TemperatureRangeRule().evaluate(DEV,
                List.of(new Measure(T0, AlarmCodes.OBIS_TEMPERATURE, 70.0, "C")),
                ctxWithoutConfig());
        assertThat(out).hasSize(1);
    }

    @Test
    @DisplayName("Temperature: 25°C nel range → no allarme")
    void tempInRange() {
        List<Alarm> out = new TemperatureRangeRule().evaluate(DEV,
                List.of(new Measure(T0, AlarmCodes.OBIS_TEMPERATURE, 25.0, "C")),
                ctxWithoutConfig());
        assertThat(out).isEmpty();
    }
}
