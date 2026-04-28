package com.aton.proj.gastelemetry.decoder.alarm;

import com.aton.proj.gastelemetry.common.Alarm;
import com.aton.proj.gastelemetry.common.DecoderContext;
import com.aton.proj.gastelemetry.common.Measure;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Emette {@link AlarmCodes#ALARM_TEMPERATURE_OUT_RANGE} quando la temperatura è
 * fuori dall'intervallo {@code [min, max]} (default {@code [-10, 60]} °C, da
 * {@link AlarmCodes#CFG_TEMP_MIN}/{@link AlarmCodes#CFG_TEMP_MAX}).
 *
 * <p>Range di default scelto per lo storage outdoor di GPL/serbatoi: sopra 60°C
 * il rischio di sovrapressione è significativo, sotto -10°C il regolatore può
 * congelare.
 */
@Component
public class TemperatureRangeRule implements AlarmRule {

    static final double DEFAULT_MIN = -10.0;
    static final double DEFAULT_MAX =  60.0;

    @Override
    public List<Alarm> evaluate(String deviceId, List<Measure> measures, DecoderContext ctx) {
        double min = ThresholdRules.doubleConfig(ctx, deviceId, AlarmCodes.CFG_TEMP_MIN, DEFAULT_MIN);
        double max = ThresholdRules.doubleConfig(ctx, deviceId, AlarmCodes.CFG_TEMP_MAX, DEFAULT_MAX);

        List<Alarm> out = new ArrayList<>();
        for (Measure m : measures) {
            if (!AlarmCodes.OBIS_TEMPERATURE.equals(m.obisCode())) continue;
            if (m.value() < min || m.value() > max) {
                out.add(new Alarm(m.timestamp(), AlarmCodes.ALARM_TEMPERATURE_OUT_RANGE,
                        "Temperature " + m.value() + "°C outside [" + min + ", " + max + "]"));
            }
        }
        return out;
    }
}
