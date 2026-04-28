package com.aton.proj.gastelemetry.decoder.alarm;

import com.aton.proj.gastelemetry.common.Alarm;
import com.aton.proj.gastelemetry.common.DecoderContext;
import com.aton.proj.gastelemetry.common.Measure;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Emette {@link AlarmCodes#ALARM_TANK_LOW} quando una misura
 * {@link AlarmCodes#OBIS_TANK_LEVEL} scende sotto la soglia configurata.
 *
 * <p>Soglia letta da {@link AlarmCodes#CFG_TANK_LOW_THRESHOLD}, default 20.
 */
@Component
public class TankLevelLowRule implements AlarmRule {

    static final double DEFAULT_THRESHOLD = 20.0;

    @Override
    public List<Alarm> evaluate(String deviceId, List<Measure> measures, DecoderContext ctx) {
        double threshold = ThresholdRules.doubleConfig(
                ctx, deviceId, AlarmCodes.CFG_TANK_LOW_THRESHOLD, DEFAULT_THRESHOLD);

        List<Alarm> out = new ArrayList<>();
        for (Measure m : measures) {
            if (AlarmCodes.OBIS_TANK_LEVEL.equals(m.obisCode()) && m.value() < threshold) {
                out.add(new Alarm(m.timestamp(), AlarmCodes.ALARM_TANK_LOW,
                        "Tank level " + m.value() + " < threshold " + threshold));
            }
        }
        return out;
    }
}
