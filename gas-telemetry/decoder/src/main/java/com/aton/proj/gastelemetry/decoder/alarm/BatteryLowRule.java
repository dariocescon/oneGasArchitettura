package com.aton.proj.gastelemetry.decoder.alarm;

import com.aton.proj.gastelemetry.common.Alarm;
import com.aton.proj.gastelemetry.common.DecoderContext;
import com.aton.proj.gastelemetry.common.Measure;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Emette {@link AlarmCodes#ALARM_BATTERY_LOW} quando la tensione di batteria
 * scende sotto la soglia (default 3200 mV, da {@link AlarmCodes#CFG_BATTERY_LOW_THRESHOLD}).
 *
 * <p>Sotto 3.0V un device litio-tionile (TEK822) si avvicina al cutoff hardware:
 * 3.2V dà ~1 mese di preavviso operativo.
 */
@Component
public class BatteryLowRule implements AlarmRule {

    static final double DEFAULT_THRESHOLD = 3200.0; // mV

    @Override
    public List<Alarm> evaluate(String deviceId, List<Measure> measures, DecoderContext ctx) {
        double threshold = ThresholdRules.doubleConfig(
                ctx, deviceId, AlarmCodes.CFG_BATTERY_LOW_THRESHOLD, DEFAULT_THRESHOLD);

        List<Alarm> out = new ArrayList<>();
        for (Measure m : measures) {
            if (AlarmCodes.OBIS_BATTERY_VOLTAGE.equals(m.obisCode()) && m.value() < threshold) {
                out.add(new Alarm(m.timestamp(), AlarmCodes.ALARM_BATTERY_LOW,
                        "Battery " + m.value() + "mV < threshold " + threshold + "mV"));
            }
        }
        return out;
    }
}
