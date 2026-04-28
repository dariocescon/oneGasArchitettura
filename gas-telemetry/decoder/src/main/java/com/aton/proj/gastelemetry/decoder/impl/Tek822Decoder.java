package com.aton.proj.gastelemetry.decoder.impl;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.aton.proj.gastelemetry.common.Alarm;
import com.aton.proj.gastelemetry.common.DecodedPacket;
import com.aton.proj.gastelemetry.common.Decoder;
import com.aton.proj.gastelemetry.common.DecoderContext;
import com.aton.proj.gastelemetry.common.Measure;

/**
 * Decoder per la famiglia TEK822 (Tekelek).
 *
 * Fonti autoritative (in ordine di priorità):
 *   1. configuration_and_command_v1.21.xlsm  — sheet "822" (decoder Excel funzionante)
 *   2. configuration_and_command_v1.21.pdf   — sezione 2.2 (manuale 9-5988-07)
 *
 * Struttura del messaggio TEK822:
 *
 *   Byte  0      : product type (8=TEK822V1, 23=TEK822V1BTN, 24=TEK822V2, …)
 *   Byte  1      : HW revision
 *   Byte  2      : FW revision
 *   Byte  3      : contact reason flags (bit3=manual, bit0=scheduled, …)
 *   Byte  4      : alarm status flags + last reset
 *   Byte  5      : signal strength (RSSI o CSQ)
 *   Byte  6      : bit6=LTE Act, bit5=RTC Set, bits[4:0]=battery percentage code
 *   Byte  7-14   : IMEI — 8 byte BCD → 16 nibble → 15 cifre (leading zero rimosso)
 *   Byte 15      : bits[5:0]=msgType, bits[7:6]=length hi
 *   Byte 16      : length lo  (declaredLength = ((b15>>6)&0x03)*256 + (b16&0xFF))
 *
 *   Per msg type 4/8/9 (measurement messages):
 *   Byte 17-18   : message count (big-endian)
 *   Byte 19      : bits[7:5]=try-attempts, bits[4:0]=RTC ore
 *   Byte 20-21   : energy used / last error code (dipende da product type e FW)
 *   Byte 22      : bit7=NB-IoT flag (combinato con byte 6 bit6 → CAT-M / 2G)
 *   Byte 23      : logger speed — 3 casi: 0x00 → 1 min, 0x80 → 15 min, altro → (b&0x7F)×15 min
 *   Byte 24      : login time (in unità da 5 secondi)
 *   Byte 25      : RTC minuti
 *   Byte 26+     : misure — fino a 28 slot × 4 byte ciascuno
 *   Ultimi 2 byte: CRC (polinomio non documentato)
 *
 *   Per ogni misura (j = 26 + i*4):
 *     byte j     : Aux2  (sanity check, attesa = 10)
 *     byte j+1   : temp raw → °C = (raw / 2.0) - 30.0  (mezzo grado di risoluzione)
 *     byte j+2   : bits[5:2] = Aux1 (sanity check, attesa = 10), bits[1:0] = dist hi
 *     byte j+3   : dist lo   → distance (cm) = ((b[j+2] & 0x03) << 8) | b[j+3]
 *
 *   Per msg type 6/16/17 (ASCII messages):
 *   Byte 17+     : payload ASCII diretto (impostazioni, statistiche, GPS)
 */
@Component
public class Tek822Decoder implements Decoder {

    private static final Logger log = LoggerFactory.getLogger(Tek822Decoder.class);

    // ---- Offset header fisso (comuni a tutti i message type) ----
    private static final int BATTERY_STATUS_OFFSET = 6;   // battery + LTE Act + RTC Set
    private static final int MSG_TYPE_BYTE         = 15;  // bits[5:0] = msgType
    private static final int MIN_HEADER_LEN        = 17;  // byte 0-16 sempre presenti

    // ---- Offset aggiuntivi per msg type 4/8/9 ----
    private static final int RTC_HH_OFFSET         = 19;  // bits[4:0] = ore (bits[7:5]=tryTickets)
    private static final int NETWORK_TECH_OFFSET   = 22;  // bit[7]=NB, altrimenti CAT-M / 2G via byte 6
    private static final int LOGGER_SPD_OFFSET     = 23;  // 3 casi: 0x00, 0x80, altri
    private static final int LOGIN_TIME_OFFSET     = 24;  // login time × 5 secondi
    private static final int RTC_MM_OFFSET         = 25;  // minuti
    private static final int PAYLOAD_OFFSET        = 26;  // inizio misure

    // ---- Offset per msg type 6/16/17 ----
    private static final int ASCII_PAYLOAD_START   = 17;  // payload ASCII subito dopo l'header fisso

    // ---- Costanti misure ----
    private static final int BYTES_PER_MEASURE     = 4;
    private static final int MAX_MEASURES          = 28;

    // ---- Lunghezza CRC trailer (gli ultimi 2 byte del payload) ----
    private static final int CRC_TRAILER_LEN       = 2;

    // ---- Flag ----
    private static final int FLAG_NB_IOT           = 0x80; // byte 22 bit7
    private static final int FLAG_LTE_ACT          = 0x40; // byte 6 bit6

    // ================================================================
    //  Entry point
    // ================================================================

    @Override
    public void doDecode(DecoderContext ctx, String deviceId, byte[] data) {
        if (data == null || data.length < MIN_HEADER_LEN) {
            log.warn("Payload troppo corto ({} byte) per device {}", data == null ? 0 : data.length, deviceId);
            return;
        }

        // Trailer CRC: gli ultimi 2 byte del payload (sheet "822" del XLSM, celle G94/G95).
        // Il polinomio CRC non è documentato nel manuale TEK822 (sezione 3.6 cita solo S3 bit 4
        // come switch on/off): per ora estraiamo e logghiamo, validazione TODO.
        logCrcTrailer(data);

        int messageType = data[MSG_TYPE_BYTE] & 0x3F;
        log.debug("Decodifico msg type {} per device {}", messageType, deviceId);

        DecodedPacket packet = switch (messageType) {
            case 4, 8, 9 -> decodeMeasures(ctx, deviceId, data);
            case 6       -> decodeSettings(data);
            case 16      -> decodeStatistics(data);
            case 17      -> decodeGps(data);
            default      -> {
                log.warn("Message type {} sconosciuto per device {}", messageType, deviceId);
                yield new DecodedPacket(List.of(), List.of());
            }
        };

        ctx.publishDecodedData(deviceId, packet);
    }

    /**
     * Estrae il CRC dichiarato dagli ultimi 2 byte del payload e lo logga.
     *
     * Validazione non implementata: il polinomio CRC non è specificato nella
     * documentazione TEK822 v1.21 (PDF sezione 3.6 e XLSM sheet "822").
     * TODO: appena disponibile l'algoritmo, ricalcolare il CRC su data[0..len-3]
     *       e confrontarlo con `declaredCrc`.
     */
    private void logCrcTrailer(byte[] data) {
        if (data.length < CRC_TRAILER_LEN) return;
        int crcHi = data[data.length - 2] & 0xFF;
        int crcLo = data[data.length - 1] & 0xFF;
        int declaredCrc = (crcHi << 8) | crcLo;
        log.debug("CRC dichiarato (byte {}-{}): 0x{} — validazione TODO (polinomio non in docs)",
                data.length - 2, data.length - 1, String.format("%04X", declaredCrc));
    }

    // ================================================================
    //  Message type 4/8/9 — misure periodiche
    // ================================================================

    private DecodedPacket decodeMeasures(DecoderContext ctx, String deviceId, byte[] data) {
        if (data.length < PAYLOAD_OFFSET) {
            log.warn("Payload troppo corto per misure: {} byte", data.length);
            return new DecodedPacket(List.of(), List.of());
        }

        Instant serverTime     = Instant.now();
        Instant baseTimestamp  = reconstructTimestamp(data, serverTime);
        long loggerSpeedSec    = resolveLoggerSpeed(data);

        List<Measure> measures = new ArrayList<>();
        List<Alarm>   alarms   = new ArrayList<>();

        // Diagnostica di rete (byte 22, 24) — riferita al timestamp della misura più recente
        measures.addAll(decodeNetworkDiagnostics(data, baseTimestamp));

        for (int i = 0; i < MAX_MEASURES; i++) {
            int j = PAYLOAD_OFFSET + i * BYTES_PER_MEASURE;
            if (j + BYTES_PER_MEASURE > data.length - CRC_TRAILER_LEN) break;

            // Salta slot vuoti (tutti a 0) — convenzione TEK822 per misure non valide
            int filter = (data[j] & 0xFF) + (data[j + 1] & 0xFF)
                       + (data[j + 2] & 0xFF) + (data[j + 3] & 0xFF);
            if (filter == 0) continue;

            Instant ts = baseTimestamp.minusSeconds(loggerSpeedSec * i);

            // Distanza (cm): bit[1:0] di byte j+2 = high, byte j+3 = low
            // (XLSM "822" C66: =(BIN2DEC(RIGHT(HEX2BIN(M66,8),2)))*256 + HEX2DEC(N66))
            int distance = ((data[j + 2] & 0x03) << 8) | (data[j + 3] & 0xFF);

            // Temperatura (°C): byte j+1 / 2.0 − 30 (mezzo grado di risoluzione)
            // PDF sample: byte=0x5B=91 → 91/2−30 = 15.5°C
            // (Maschera & 0x7F per sicurezza: bit 7 non documentato)
            double temperatureC = ((data[j + 1] & 0x7F) / 2.0) - 30.0;

            // Aux1 = "4 major bits starting at bit 5 of byte j+2" (PDF sezione 2.2.2.2)
            //        bits[5:2] di byte j+2 — should always be 10 (sanity check)
            int aux1 = (data[j + 2] >> 2) & 0x0F;

            // Aux2 = byte j (intero, non mascherato)
            //        XLSM "822" E66: =HEX2DEC(K66) — should always be 10 (sanity check)
            int aux2 = data[j] & 0xFF;

            measures.add(new Measure(ts, "distance_cm",   distance,     "cm"));
            measures.add(new Measure(ts, "temperature_c", temperatureC, "°C"));
            measures.add(new Measure(ts, "aux1",          aux1,         ""));
            measures.add(new Measure(ts, "aux2",          aux2,         ""));
        }

        alarms.addAll(checkConfigAlarms(ctx, deviceId, measures));

        return new DecodedPacket(measures, alarms);
    }

    /**
     * Decodifica le informazioni diagnostiche di rete dai byte 22 e 24.
     *
     * Byte 22 (XLSM "822" C61): tecnologia di rete utilizzata.
     *   - bit 7 = 1               → NB-IoT
     *   - bit 7 = 0, byte 6 bit 6 = 1 → CAT-M
     *   - bit 7 = 0, byte 6 bit 6 = 0 → 2G
     *
     * Byte 24 (XLSM "822" C63): login time = byte × 5 secondi
     */
    private List<Measure> decodeNetworkDiagnostics(byte[] data, Instant timestamp) {
        List<Measure> diag = new ArrayList<>();

        if (data.length > NETWORK_TECH_OFFSET) {
            int b22 = data[NETWORK_TECH_OFFSET] & 0xFF;
            // Codifica tecnologia come valore numerico: 0=2G, 1=CAT-M, 2=NB-IoT
            int techCode;
            if ((b22 & FLAG_NB_IOT) != 0) {
                techCode = 2;
            } else if ((data[BATTERY_STATUS_OFFSET] & FLAG_LTE_ACT) != 0) {
                techCode = 1;
            } else {
                techCode = 0;
            }
            diag.add(new Measure(timestamp, "network.tech_code", techCode, ""));
        }

        if (data.length > LOGIN_TIME_OFFSET) {
            int loginTimeSec = (data[LOGIN_TIME_OFFSET] & 0xFF) * 5;
            diag.add(new Measure(timestamp, "network.login_time_s", loginTimeSec, "s"));
        }

        return diag;
    }

    /**
     * Ricostruisce il timestamp base dal RTC del device.
     *
     * Il device invia solo ore (byte 19 bits[4:0]) e minuti (byte 25).
     * Il server aggiunge la data corrente. Se il clock del device è
     * significativamente avanti rispetto al server (midnight wrap, es. RTC=23:50
     * con server a 00:05), il pacchetto è di ieri.
     *
     * Portato da: TekMessageDecoder.decodeMeasurementData() (onGas_Meteor_claude)
     */
    private Instant reconstructTimestamp(byte[] data, Instant serverTime) {
        int rtcHours   = data[RTC_HH_OFFSET] & 0x1F;
        int rtcMinutes = data[RTC_MM_OFFSET]  & 0xFF;

        LocalDate serverDate = serverTime.atZone(ZoneOffset.UTC).toLocalDate();
        long baseMs = serverDate
                .atTime(LocalTime.of(rtcHours, rtcMinutes, 0))
                .atZone(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();

        long serverMs = serverTime.toEpochMilli();

        // Se l'RTC è >12 ore avanti rispetto al server → giorno precedente
        if (baseMs - serverMs > 12L * 60 * 60 * 1000) {
            baseMs -= 24L * 60 * 60 * 1000;
        }

        return Instant.ofEpochMilli(baseMs);
    }

    /**
     * Calcola l'intervallo tra campioni in secondi.
     *
     * Regola (PDF sezione 2.2.2.1, confermata da XLSM sheet "822" cella C62):
     *   IF(byte23 == 0x00) → 1 minuto
     *   ELSE IF(byte23 == 0x80) → 15 minuti
     *   ELSE → (byte23 & 0x7F) × 15 minuti
     *
     * Nessuna distinzione per msgType (4 vs 8 vs 9) né eccezione per "manual contact":
     * questi erano artefatti dell'implementazione TekMessageDecoder.java in
     * onGas_Meteor_claude, non presenti nella specifica.
     */
    private long resolveLoggerSpeed(byte[] data) {
        int b23 = data[LOGGER_SPD_OFFSET] & 0xFF;
        int minutes;
        if (b23 == 0x00) {
            minutes = 1;
        } else if (b23 == 0x80) {
            minutes = 15;
        } else {
            minutes = (b23 & 0x7F) * 15;
        }
        return minutes * 60L; // converti in secondi
    }

    // ================================================================
    //  Message type 6 — settings (ASCII: "S0=80,S1=05,…")
    // ================================================================

    private DecodedPacket decodeSettings(byte[] data) {
        String ascii = extractAsciiPayload(data);
        List<Measure> measures = new ArrayList<>();
        Instant now = Instant.now();

        for (String setting : ascii.split(",")) {
            if (!setting.contains("=")) continue;
            String[] parts = setting.split("=", 2);
            String key   = "setting." + parts[0].trim();
            String value = parts.length > 1 ? parts[1].trim() : "";
            try {
                measures.add(new Measure(now, key, Double.parseDouble(value), ""));
            } catch (NumberFormatException e) {
                // Valori non numerici (es. APN, hostname) non inseribili come Measure
                log.debug("Setting non numerico ignorato — {}={}", key, value);
            }
        }

        log.debug("Msg type 6: parsati {} setting(s)", measures.size());
        return new DecodedPacket(measures, List.of());
    }

    // ================================================================
    //  Message type 16 — statistics (ASCII CSV)
    //  Format: ICCID,energyUsed,minTemp,maxTemp,msgCount,failCount,
    //          totalSendTime,maxSendTime,minSendTime,rssiTotal,rssiValidCount,rssiFailCount
    // ================================================================

    private DecodedPacket decodeStatistics(byte[] data) {
        String ascii  = extractAsciiPayload(data);
        String[] flds = ascii.split(",");

        List<Measure> measures = new ArrayList<>();
        Instant now = Instant.now();

        if (flds.length >= 12) {
            safeAdd(measures, now, "stats.energy_used_mah",   flds[1],  "mAh");
            safeAdd(measures, now, "stats.min_temperature_c", flds[2],  "°C");
            safeAdd(measures, now, "stats.max_temperature_c", flds[3],  "°C");
            safeAdd(measures, now, "stats.message_count",     flds[4],  "");
            safeAdd(measures, now, "stats.delivery_fail",     flds[5],  "");
            safeAdd(measures, now, "stats.rssi_valid_count",  flds[10], "");
            safeAdd(measures, now, "stats.rssi_fail_count",   flds[11], "");
            // flds[0] = ICCID (stringa, non inserita come Measure)
            log.debug("Msg type 16: ICCID={}, parsed {} stat(s)", flds[0].trim(), measures.size());
        } else {
            log.warn("Msg type 16: campo attesi ≥12, trovati {}", flds.length);
        }

        return new DecodedPacket(measures, List.of());
    }

    // ================================================================
    //  Message type 17 — GPS (ASCII CSV)
    //  Format: timeToFix,utcTime,latRaw,lonRaw,hPrec,alt,mode,
    //          heading,speedKmh,speedKnots,date,satellites
    // ================================================================

    private DecodedPacket decodeGps(byte[] data) {
        String ascii  = extractAsciiPayload(data);
        String[] flds = ascii.split(",");

        List<Measure> measures = new ArrayList<>();
        Instant now = Instant.now();

        if (flds.length >= 12) {
            safeAdd(measures, now, "gps.time_to_fix_s", flds[0],  "s");
            safeAdd(measures, now, "gps.altitude_m",    flds[5],  "m");
            safeAdd(measures, now, "gps.speed_kmh",     flds[8],  "km/h");
            safeAdd(measures, now, "gps.satellites",    flds[11], "");
            // latRaw/lonRaw sono stringhe NMEA (es. "5255.9950N") — non convertite per ora
            log.debug("Msg type 17: lat={}, lon={}, alt={}m, sats={}",
                    flds[2].trim(), flds[3].trim(), flds[5].trim(), flds[11].trim());
        } else {
            log.warn("Msg type 17: campi attesi ≥12, trovati {}", flds.length);
        }

        return new DecodedPacket(measures, List.of());
    }

    // ================================================================
    //  Allarmi da configurazione
    // ================================================================

    private List<Alarm> checkConfigAlarms(DecoderContext ctx, String deviceId, List<Measure> measures) {
        List<Alarm> alarms = new ArrayList<>();

        // Allarme livello serbatoio
        Double tankMin = ctx.getConfig(deviceId, "alarm.tank.level.min");
        if (tankMin != null) {
            measures.stream()
                    .filter(m -> "distance_cm".equals(m.obisCode()) && m.value() < tankMin)
                    .forEach(m -> alarms.add(new Alarm(
                            m.timestamp(),
                            "TANK_LEVEL_LOW",
                            "Level %.1f cm below threshold %.1f cm".formatted(m.value(), tankMin)
                    )));
        }

        // TODO: aggiungere altri allarmi da config (batteria scarica, assenza trasmissione, …)

        return alarms;
    }

    // ================================================================
    //  Utility
    // ================================================================

    /**
     * Estrae il payload ASCII per msg type 6/16/17.
     * I byte a partire da ASCII_PAYLOAD_START (17) sono caratteri ASCII diretti.
     * Rimuove la virgola iniziale se presente (alcuni campi iniziano con ',').
     */
    private String extractAsciiPayload(byte[] data) {
        if (data.length <= ASCII_PAYLOAD_START) return "";
        String raw = new String(data, ASCII_PAYLOAD_START,
                data.length - ASCII_PAYLOAD_START, StandardCharsets.US_ASCII);
        return raw.startsWith(",") ? raw.substring(1) : raw;
    }

    private void safeAdd(List<Measure> measures, Instant ts, String key, String raw, String unit) {
        if (raw == null || raw.isBlank()) return;
        try {
            measures.add(new Measure(ts, key, Double.parseDouble(raw.trim()), unit));
        } catch (NumberFormatException e) {
            log.debug("Valore non numerico per {}: '{}'", key, raw);
        }
    }
}
