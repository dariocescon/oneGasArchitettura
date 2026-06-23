package com.aton.proj.gastelemetry.decodetool.service;

import com.aton.proj.gastelemetry.common.Alarm;
import com.aton.proj.gastelemetry.common.Measure;
import com.aton.proj.gastelemetry.decoder.alarm.AlarmCodes;
import com.aton.proj.gastelemetry.decodetool.rest.AlarmView;
import com.aton.proj.gastelemetry.decodetool.rest.MeasureView;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Annotatore: associa a ogni {@link Measure} / {@link Alarm} prodotti dal
 * {@code Tek822Decoder} i metadati di provenienza:
 * <ul>
 *   <li><b>sourceHex</b>: i byte del payload originale (es. "0932")</li>
 *   <li><b>byteRange</b>: la posizione, "N" per singolo byte o "N-M" per range</li>
 * </ul>
 *
 * <p>La mappatura obisCode → (offset, length) è statica per i campi
 * dell'header (byte 0-25). Per le misure time-series (slot 4-byte a partire
 * dal byte 26) calcola dinamicamente l'offset reale saltando gli slot vuoti
 * coerentemente con il decoder.
 */
public final class HexSlicer {

    private static final int FIRST_MEASURE_OFFSET = 26;
    private static final int BYTES_PER_MEASURE_SLOT = 4;
    private static final int MAX_MEASURE_SLOTS = 28;
    private static final int CRC_TRAILER_LEN = 2;

    /** Mappa obisCode → [offset, length] per i campi a posizione fissa nell'header. */
    private static final Map<String, int[]> FIXED_OFFSETS = new HashMap<>();
    static {
        FIXED_OFFSETS.put("header.product_type",            new int[]{0,  1});
        FIXED_OFFSETS.put("header.hw_revision",             new int[]{1,  1});
        FIXED_OFFSETS.put("header.fw_revision",             new int[]{2,  1});
        FIXED_OFFSETS.put("contact_reason_flags",           new int[]{3,  1});
        FIXED_OFFSETS.put("alarm_status_flags",             new int[]{4,  1});
        FIXED_OFFSETS.put("header.gsm_rssi",                new int[]{5,  1});
        FIXED_OFFSETS.put("header.battery_percent",         new int[]{6,  1});
        FIXED_OFFSETS.put("header.rtc_set",                 new int[]{6,  1});
        FIXED_OFFSETS.put("header.lte_active",              new int[]{6,  1});
        FIXED_OFFSETS.put("header.message_count",           new int[]{17, 2});
        FIXED_OFFSETS.put("header.try_tickets_remaining",   new int[]{19, 1});
        FIXED_OFFSETS.put("header.energy_used_mah",         new int[]{20, 2});
        FIXED_OFFSETS.put("network.tech_code",              new int[]{22, 1});
        FIXED_OFFSETS.put("network.mnc",                    new int[]{22, 1});
        FIXED_OFFSETS.put("network.login_time_s",           new int[]{24, 1});
    }

    private HexSlicer() {}

    /**
     * Estrae una slice di {@code length} byte da {@code offset} come stringa
     * esadecimale uppercase. Restituisce {@code null} fuori dai bound.
     */
    public static String sliceHex(byte[] data, int offset, int length) {
        if (data == null || offset < 0 || offset + length > data.length) return null;
        StringBuilder sb = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X", data[offset + i] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Formatta una posizione/lunghezza come range leggibile:
     * <ul>
     *   <li>length = 1 → "{offset}" (es. "3")</li>
     *   <li>length &gt; 1 → "{offset}-{offset+length-1}" (es. "7-14")</li>
     * </ul>
     */
    public static String formatByteRange(int offset, int length) {
        if (length <= 0) return "";
        if (length == 1) return String.valueOf(offset);
        return offset + "-" + (offset + length - 1);
    }

    /**
     * Calcola gli offset di byte dei soli slot misure NON vuoti, nello stesso
     * ordine in cui il decoder li emette.
     */
    static List<Integer> findNonEmptySlotOffsets(byte[] payload) {
        int payloadEnd = payload.length - CRC_TRAILER_LEN;
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < MAX_MEASURE_SLOTS; i++) {
            int j = FIRST_MEASURE_OFFSET + i * BYTES_PER_MEASURE_SLOT;
            if (j + BYTES_PER_MEASURE_SLOT > payloadEnd) break;
            int sum = (payload[j] & 0xFF) + (payload[j + 1] & 0xFF)
                    + (payload[j + 2] & 0xFF) + (payload[j + 3] & 0xFF);
            if (sum != 0) offsets.add(j);
        }
        return offsets;
    }

    /**
     * Restituisce la coppia {@code (offset, length)} per una misura time-series
     * all'interno di uno slot da 4 byte che inizia a {@code slotOffset}.
     * Ritorna {@code null} se l'obisCode non è una misura time-series.
     */
    private static int[] slotMeasureSlice(String obisCode, int slotOffset) {
        return switch (obisCode) {
            case "aux2"          -> new int[]{slotOffset,     1};
            case "temperature_c" -> new int[]{slotOffset + 1, 1};
            case "aux1"          -> new int[]{slotOffset + 2, 1};
            case "distance_cm"   -> new int[]{slotOffset + 2, 2};
            default -> null;
        };
    }

    private static boolean isPerSlotMeasure(String obisCode) {
        return "distance_cm".equals(obisCode)
                || "temperature_c".equals(obisCode)
                || "aux1".equals(obisCode)
                || "aux2".equals(obisCode);
    }

    /**
     * Annota ciascuna {@link Measure} con il byte sorgente (hex + range).
     *
     * <p>Per le misure time-series usa il <i>timestamp</i> come chiave di
     * raggruppamento: misure con lo stesso timestamp = stesso slot.
     */
    public static List<MeasureView> annotateMeasures(byte[] payload, List<Measure> measures) {
        List<Integer> slotOffsets = findNonEmptySlotOffsets(payload);
        List<MeasureView> out = new ArrayList<>(measures.size());

        int slotIndex = -1;
        Instant currentSlotTs = null;

        for (Measure m : measures) {
            String hex = null;
            String byteRange = null;

            int[] fixed = FIXED_OFFSETS.get(m.obisCode());
            if (fixed != null) {
                hex       = sliceHex(payload, fixed[0], fixed[1]);
                byteRange = formatByteRange(fixed[0], fixed[1]);
            } else if (isPerSlotMeasure(m.obisCode())) {
                if (currentSlotTs == null || !m.timestamp().equals(currentSlotTs)) {
                    currentSlotTs = m.timestamp();
                    slotIndex++;
                }
                if (slotIndex < slotOffsets.size()) {
                    int[] slice = slotMeasureSlice(m.obisCode(), slotOffsets.get(slotIndex));
                    if (slice != null) {
                        hex       = sliceHex(payload, slice[0], slice[1]);
                        byteRange = formatByteRange(slice[0], slice[1]);
                    }
                }
            }
            out.add(new MeasureView(m.timestamp(), m.obisCode(), m.value(),
                    m.unit(), hex, byteRange));
        }
        return out;
    }

    /**
     * Annota ciascun {@link Alarm} con il byte sorgente. Gli allarmi device
     * (Limit 1/2/3, Bund) derivano tutti dal byte 4 dell'header.
     */
    public static List<AlarmView> annotateAlarms(byte[] payload, List<Alarm> alarms) {
        List<AlarmView> out = new ArrayList<>(alarms.size());
        String byte4Hex = sliceHex(payload, 4, 1);
        String byte4Range = formatByteRange(4, 1);
        for (Alarm a : alarms) {
            boolean isDeviceAlarm = switch (a.alarmCode()) {
                case AlarmCodes.ALARM_DEVICE_LIMIT_1,
                     AlarmCodes.ALARM_DEVICE_LIMIT_2,
                     AlarmCodes.ALARM_DEVICE_LIMIT_3,
                     AlarmCodes.ALARM_DEVICE_BUND_STATUS -> true;
                default -> false;
            };
            out.add(new AlarmView(
                    a.timestamp(),
                    a.alarmCode(),
                    a.description(),
                    isDeviceAlarm ? byte4Hex : null,
                    isDeviceAlarm ? byte4Range : null));
        }
        return out;
    }
}
