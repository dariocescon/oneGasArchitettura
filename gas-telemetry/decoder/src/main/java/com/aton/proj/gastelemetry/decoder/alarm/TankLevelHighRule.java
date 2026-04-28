package com.aton.proj.gastelemetry.decoder.alarm;

import com.aton.proj.gastelemetry.common.Alarm;
import com.aton.proj.gastelemetry.common.DecoderContext;
import com.aton.proj.gastelemetry.common.Measure;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Emette {@link AlarmCodes#ALARM_TANK_HIGH} quando il tank level supera la soglia
 * (default 90, da {@link AlarmCodes#CFG_TANK_HIGH_THRESHOLD}).
 */
@Component
public class TankLevelHighRule implements AlarmRule {

    static final double DEFAULT_THRESHOLD = 90.0;

    @Override
    public List<Alarm> evaluate(String deviceId, List<Measure> measures, DecoderContext ctx) {
        double threshold = ThresholdRules.doubleConfig(
                ctx, deviceId, AlarmCodes.CFG_TANK_HIGH_THRESHOLD, DEFAULT_THRESHOLD);

        List<Alarm> out = new ArrayList<>();
        for (Measure m : measures) {
            if (AlarmCodes.OBIS_TANK_LEVEL.equals(m.obisCode()) && m.value() > threshold) {
                out.add(new Alarm(m.timestamp(), AlarmCodes.ALARM_TANK_HIGH,
                        "Tank level " + m.value() + " > threshold " + threshold));
            }
        }
        return out;
    }
}
