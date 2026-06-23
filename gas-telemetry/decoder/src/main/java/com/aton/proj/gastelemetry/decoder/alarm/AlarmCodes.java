package com.aton.proj.gastelemetry.decoder.alarm;

/**
 * Costanti usate dalle {@link AlarmRule}: identificatori OBIS-like delle grandezze
 * monitorate e codici degli allarmi prodotti.
 *
 * <p>Le grandezze {@link #OBIS_TANK_LEVEL}, {@link #OBIS_BATTERY_VOLTAGE},
 * {@link #OBIS_TEMPERATURE} sono i valori di {@code Measure.obisCode()} attesi
 * per le rispettive misure. Il decoder TEK822 emette le misure con questi
 * identificatori sintetici (TEK non usa OBIS standard IEC 62056).
 */
public final class AlarmCodes {

    private AlarmCodes() {}

    // ---- obisCode delle misure monitorate ----
    public static final String OBIS_TANK_LEVEL       = "TANK_LEVEL";        // % o cm
    public static final String OBIS_BATTERY_VOLTAGE  = "BATTERY_VOLTAGE";   // mV
    public static final String OBIS_TEMPERATURE      = "TEMPERATURE";       // °C

    // ---- alarmCode degli allarmi prodotti ----
    public static final String ALARM_TANK_LOW              = "TANK_LEVEL_LOW";
    public static final String ALARM_TANK_HIGH             = "TANK_LEVEL_HIGH";
    public static final String ALARM_BATTERY_LOW           = "BATTERY_LOW";
    public static final String ALARM_TEMPERATURE_OUT_RANGE = "TEMPERATURE_OUT_OF_RANGE";
    public static final String ALARM_MISSED_TRANSMISSION   = "MISSED_TRANSMISSION";

    // ---- Allarmi auto-segnalati dal device TEK822 (byte 4 dell'header) ----
    // Vedi PDF §2.2.1.3 e XLSM sheet "822" colonna "Alarm/Status".
    // Questi flag sono settati dal firmware quando una misura supera le soglie
    // configurate sui registri S4/S5/S6 del device (NON sui nostri config server-side).
    public static final String ALARM_DEVICE_LIMIT_1        = "DEVICE_LIMIT_1";       // S4 superato
    public static final String ALARM_DEVICE_LIMIT_2        = "DEVICE_LIMIT_2";       // S5 superato
    public static final String ALARM_DEVICE_LIMIT_3        = "DEVICE_LIMIT_3";       // S6 superato
    public static final String ALARM_DEVICE_BUND_STATUS    = "DEVICE_BUND_STATUS";   // sportellino contenitivo

    // ---- chiavi di configurazione (tabella device_config) ----
    public static final String CFG_TANK_LOW_THRESHOLD     = "alarm.tank.low_threshold";
    public static final String CFG_TANK_HIGH_THRESHOLD    = "alarm.tank.high_threshold";
    public static final String CFG_BATTERY_LOW_THRESHOLD  = "alarm.battery.low_threshold";
    public static final String CFG_TEMP_MIN               = "alarm.temperature.min";
    public static final String CFG_TEMP_MAX               = "alarm.temperature.max";
    public static final String CFG_MISSED_TX_SECONDS      = "alarm.missed_transmission.threshold_seconds";
}
