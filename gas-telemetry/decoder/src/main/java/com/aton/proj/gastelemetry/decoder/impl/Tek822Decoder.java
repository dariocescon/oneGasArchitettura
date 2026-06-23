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
import com.aton.proj.gastelemetry.decoder.alarm.AlarmCodes;

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
    private static final int PRODUCT_TYPE_OFFSET   = 0;   // codice prodotto (0x18=TEK822 V2 NB, ecc.)
    private static final int HW_REV_OFFSET         = 1;   // hardware revision (raw byte)
    private static final int FW_REV_OFFSET         = 2;   // firmware revision (raw byte)
    private static final int CONTACT_REASON_OFFSET = 3;   // bitmask motivo invio
    private static final int ALARM_STATUS_OFFSET   = 4;   // bitmask flag allarme del device
    private static final int CSQ_OFFSET            = 5;   // Cellular Signal Quality 0-31
    private static final int BATTERY_STATUS_OFFSET = 6;   // battery + LTE Act + RTC Set
    private static final int MSG_TYPE_BYTE         = 15;  // bits[5:0] = msgType
    private static final int MIN_HEADER_LEN        = 17;  // byte 0-16 sempre presenti

    // ---- Offset diagnostiche specifiche di Msg #4/#8/#9 ----
    private static final int MSG_COUNT_OFFSET      = 17;  // 17-18: counter messaggi uint16 BE
    private static final int TRY_TICKETS_OFFSET    = 19;  // bits[7:5] = retry rimanenti, bits[4:0] = RTC hh
    private static final int ENERGY_USED_OFFSET    = 20;  // 20-21: energy used uint16 BE (mAh)

    // ---- Maschere bit del byte 6 (Battery/Status) ----
    private static final int BATTERY_PERCENT_MASK  = 0x1F; // bits[4:0]
    private static final int FLAG_RTC_SET          = 0x20; // bit 5

    // ---- Maschere per i flag d'allarme del byte 4 (PDF §2.2.1.3) ----
    private static final int FLAG_LIMIT_1     = 0x01; // bit 0
    private static final int FLAG_LIMIT_2     = 0x02; // bit 1
    private static final int FLAG_LIMIT_3     = 0x04; // bit 2
    private static final int FLAG_BUND_STATUS = 0x08; // bit 3

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
    //  Helper statici esposti pubblicamente
    // ================================================================

    /**
     * Calcola la lunghezza del body dichiarata dal device a partire dai byte
     * 15 e 16 dell'header (PDF §2.2.1).
     *
     * <p>Formula: {@code ((byte15 >> 6) & 0x03) × 256 + byte16}.
     *
     * <p>I bit 7 e 6 del byte 15 sono il contributo "alto" (× 256), il byte 16
     * è il contributo "basso". I bit 5:0 di byte 15 sono dedicati al Message
     * Type (vedi {@code & 0x3F} in {@link #doDecode}), quindi NON possono
     * essere usati per la lunghezza — il PDF v1.21 cita erroneamente "Bit5&Bit4"
     * ma la formula coerente con i message type 16/17 (che usano bit 4) usa
     * bits[7:6].
     *
     * <p>Per i TEK822 reali il body sta sempre in un solo byte (max documentato
     * 235), quindi i bit alti sono di fatto sempre 0 e il valore coincide con
     * il solo byte 16.
     *
     * @param data payload TEK822 di almeno {@value #MIN_HEADER_LEN} byte
     * @return lunghezza body, range 0-1023
     * @throws ArrayIndexOutOfBoundsException se {@code data} è più corto dell'header
     */
    public static int computeDeclaredBodyLength(byte[] data) {
        return ((data[MSG_TYPE_BYTE] >> 6) & 0x03) * 256
                + (data[MSG_TYPE_BYTE + 1] & 0xFF);
    }

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

        // Timestamp base: per Msg #4/#8/#9 ricostruito dal RTC del device (byte 19+25),
        // per gli altri tipi è il server time (l'header non porta RTC per #6/#16/#17).
        Instant baseTimestamp = isMeasureMessage(messageType)
                ? reconstructTimestamp(data, Instant.now())
                : Instant.now();

        // Diagnostica COMUNE (Fix A): l'header (byte 0-6) e i flag (byte 3, 4)
        // esistono per QUALSIASI tipo di messaggio — l'header è condiviso. Li
        // emettiamo qui per non duplicare le chiamate nei rami switch.
        List<Measure> measures = new ArrayList<>();
        List<Alarm>   alarms   = new ArrayList<>();
        measures.addAll(decodeHeaderDiagnostics(data, baseTimestamp));
        measures.add(new Measure(baseTimestamp, "contact_reason_flags",
                data[CONTACT_REASON_OFFSET] & 0xFF, ""));
        measures.add(new Measure(baseTimestamp, "alarm_status_flags",
                data[ALARM_STATUS_OFFSET] & 0xFF, ""));
        alarms.addAll(decodeDeviceAlarms(data, baseTimestamp));

        // Dispatch al decoder body-specifico
        DecodedPacket body = switch (messageType) {
            case 4, 8, 9 -> decodeMeasureBody(ctx, deviceId, data, baseTimestamp);
            case 6       -> decodeSettings(data, baseTimestamp);
            case 16      -> decodeStatistics(data, baseTimestamp);
            case 17      -> decodeGps(data, baseTimestamp);
            default      -> {
                log.warn("Message type {} sconosciuto per device {}", messageType, deviceId);
                yield new DecodedPacket(List.of(), List.of());
            }
        };

        measures.addAll(body.measures());
        alarms.addAll(body.alarms());

        ctx.publishDecodedData(deviceId, new DecodedPacket(measures, alarms));
    }

    private static boolean isMeasureMessage(int messageType) {
        return messageType == 4 || messageType == 8 || messageType == 9;
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

    private DecodedPacket decodeMeasureBody(DecoderContext ctx, String deviceId, byte[] data, Instant baseTimestamp) {
        if (data.length < PAYLOAD_OFFSET) {
            log.warn("Payload troppo corto per misure: {} byte", data.length);
            return new DecodedPacket(List.of(), List.of());
        }

        long loggerSpeedSec = resolveLoggerSpeed(data);

        List<Measure> measures = new ArrayList<>();
        List<Alarm>   alarms   = new ArrayList<>();

        // Diagnostica specifica Msg #4/#8/#9 (byte 17-21): message count, try
        // tickets, energy used. XLSM "822" righe R0056-R0060.
        measures.addAll(decodeMeasureMessageDiagnostics(data, baseTimestamp));

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
            // PDF §2.2.2.2: il byte va trattato come unsigned intero (0..255),
            // range risultante [-30.0, +97.5] °C. Una precedente maschera & 0x7F
            // tagliava il bit 7 introducendo errori fino a 64°C su valori ≥ 0x80.
            double temperatureC = ((data[j + 1] & 0xFF) / 2.0) - 30.0;

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
     * Estrae le diagnostiche presenti nei byte 0-6 dell'header (validi per
     * <i>qualsiasi</i> message type, perché l'header è condiviso).
     *
     * <p>Campi prodotti (tutti sotto il prefisso {@code header.}):
     * <ul>
     *   <li>{@code product_type} — codice prodotto raw (byte 0): es. 0x18 = TEK822 V2 NB</li>
     *   <li>{@code hw_revision} / {@code fw_revision} — byte 1 e 2 grezzi</li>
     *   <li>{@code csq} — Cellular Signal Quality 0-31 (byte 5)</li>
     *   <li>{@code battery_percent} — {@code (byte6 bits[4:0]) × 100 / 31}, arrotondato a 1 decimale</li>
     *   <li>{@code rtc_set} / {@code lte_active} — flag binari (byte 6 bit 5/6)</li>
     * </ul>
     *
     * <p>Mirror dei valori mostrati dallo sheet "822" dell'XLSM v1.21.
     */
    private List<Measure> decodeHeaderDiagnostics(byte[] data, Instant timestamp) {
        List<Measure> diag = new ArrayList<>();

        // Byte 0 — codice prodotto + label umano (XLSM colonna H)
        int productCode = data[PRODUCT_TYPE_OFFSET] & 0xFF;
        diag.add(new Measure(timestamp, "header.product_type",
                productCode, productTypeLabel(productCode)));

        // Byte 1 — HW revision: PDF §2.2.1: "Major 5 bits + Minor 3 bits".
        // L'XLSM display format è "{minor}.{major}" (es. 0x02 → "2.0") con
        // il modem identificato dalla parte major (0=BG96, 1=BG95).
        int b1 = data[HW_REV_OFFSET] & 0xFF;
        int hwMinor = b1 & 0x07;        // 3 bit bassi
        int hwMajor = (b1 >> 3) & 0x1F; // 5 bit alti
        double hwVersion = hwMinor + hwMajor / 10.0;
        diag.add(new Measure(timestamp, "header.hw_revision",
                hwVersion, modemLabel(hwMajor)));

        // Byte 2 — FW revision: PDF §2.2.1: "Minor 5 bits = FW Major, Major 3 bits = FW Minor".
        // Display format "{fwMajor}.{fwMinor}" → es. 0x03 → "FW 3.0".
        int b2 = data[FW_REV_OFFSET] & 0xFF;
        int fwMajor = b2 & 0x1F;        // 5 bit bassi
        int fwMinor = (b2 >> 5) & 0x07; // 3 bit alti
        double fwVersion = fwMajor + fwMinor / 10.0;
        diag.add(new Measure(timestamp, "header.fw_revision",
                fwVersion, "FW " + fwMajor + "." + fwMinor));

        // Byte 5 — RSSI (la colonna XLSM è etichettata "GSM Rssi")
        diag.add(new Measure(timestamp, "header.gsm_rssi", data[CSQ_OFFSET] & 0xFF, ""));

        // Byte 6 — Battery percentage. XLSM mostra "70,97% Capacity Remaining"
        // (2 decimali); usiamo la stessa precisione.
        int b6 = data[BATTERY_STATUS_OFFSET] & 0xFF;
        double batteryPercent = ((b6 & BATTERY_PERCENT_MASK) * 100.0) / 31.0;
        diag.add(new Measure(timestamp, "header.battery_percent",
                Math.round(batteryPercent * 100.0) / 100.0, "% Capacity Remaining"));
        diag.add(new Measure(timestamp, "header.rtc_set",
                (b6 & FLAG_RTC_SET) != 0 ? 1 : 0, ""));
        diag.add(new Measure(timestamp, "header.lte_active",
                (b6 & FLAG_LTE_ACT) != 0 ? 1 : 0, ""));

        return diag;
    }

    /**
     * Mappa il codice prodotto (byte 0) al modello commerciale come fa la
     * colonna I dello sheet "822" XLSM.
     */
    private static String productTypeLabel(int code) {
        return switch (code) {
            case 0x07 -> "TEK586";
            case 0x08 -> "TEK822 V1";
            case 0x09 -> "TEK643";
            case 0x0B -> "TEK733";
            case 0x0C -> "TEK811";
            case 0x10 -> "TEK898";
            case 0x11 -> "TEK871";
            case 0x12 -> "TEK733A";
            case 0x13 -> "TEK871A";
            case 0x14 -> "TEK811A";
            case 0x17 -> "TEK822 V1 BTN";
            case 0x18 -> "TEK822 V2 NB";
            default   -> String.format("Unknown (0x%02X)", code);
        };
    }

    /**
     * Mappa la parte major dell'HW revision (byte 1 bits[7:3]) al modem
     * cellulare integrato, come fa la colonna I dello sheet "822" XLSM.
     */
    private static String modemLabel(int hwMajor) {
        return switch (hwMajor) {
            case 0 -> "BG96";
            case 1 -> "BG95";
            default -> "HW major=" + hwMajor;
        };
    }

    /**
     * Estrae le diagnostiche specifiche dei messaggi #4/#8/#9 dai byte 17-21.
     *
     * <p>Campi prodotti:
     * <ul>
     *   <li>{@code header.message_count} — counter cumulativo del device (byte 17-18 BE)</li>
     *   <li>{@code header.try_tickets_remaining} — retry rimanenti (byte 19 bits[7:5])</li>
     *   <li>{@code header.energy_used_mah} — energia consumata in mAh (byte 20-21 BE)</li>
     * </ul>
     *
     * <p>Il metodo verifica la lunghezza del payload prima di leggere ciascun byte.
     */
    private List<Measure> decodeMeasureMessageDiagnostics(byte[] data, Instant timestamp) {
        List<Measure> diag = new ArrayList<>();

        if (data.length > MSG_COUNT_OFFSET + 1) {
            int msgCount = ((data[MSG_COUNT_OFFSET] & 0xFF) << 8)
                    | (data[MSG_COUNT_OFFSET + 1] & 0xFF);
            diag.add(new Measure(timestamp, "header.message_count", msgCount, ""));
        }

        if (data.length > TRY_TICKETS_OFFSET) {
            int tryTickets = (data[TRY_TICKETS_OFFSET] >> 5) & 0x07;
            diag.add(new Measure(timestamp, "header.try_tickets_remaining", tryTickets, ""));
        }

        if (data.length > ENERGY_USED_OFFSET + 1) {
            int energyMah = ((data[ENERGY_USED_OFFSET] & 0xFF) << 8)
                    | (data[ENERGY_USED_OFFSET + 1] & 0xFF);
            diag.add(new Measure(timestamp, "header.energy_used_mah", energyMah, "mAh"));
        }

        return diag;
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
            // Codifica tecnologia: bit 7 del byte 22 → NB-IoT.
            // Se bit 7 = 0, distinguiamo CAT-M (LTE Act = 1) da 2G (LTE Act = 0).
            // L'XLSM mostra le label "NB" / "CATM" / "GSM" — riportiamole nel campo unit.
            int techCode;
            String techLabel;
            if ((b22 & FLAG_NB_IOT) != 0) {
                techCode = 2;
                techLabel = "NB";
            } else if ((data[BATTERY_STATUS_OFFSET] & FLAG_LTE_ACT) != 0) {
                techCode = 1;
                techLabel = "CATM";
            } else {
                techCode = 0;
                techLabel = "GSM";
            }
            diag.add(new Measure(timestamp, "network.tech_code", techCode, techLabel));

            // MNC: i 4 bit bassi del byte 22 codificano l'operator short code
            // (XLSM colonna "NB/MNC" — per byte 0xFF mostra "15" = 0xFF & 0x0F).
            diag.add(new Measure(timestamp, "network.mnc", b22 & 0x0F, ""));
        }

        if (data.length > LOGIN_TIME_OFFSET) {
            int loginTimeSec = (data[LOGIN_TIME_OFFSET] & 0xFF) * 5;
            diag.add(new Measure(timestamp, "network.login_time_s", loginTimeSec, "s"));
        }

        return diag;
    }

    /**
     * Estrae gli allarmi che il device stesso ha segnalato nel byte 4 dell'header
     * (PDF §2.2.1.3, XLSM sheet "822" colonna "Alarm/Status").
     *
     * Sono <b>indipendenti</b> dagli allarmi calcolati server-side ({@link AlarmRule}):
     * questi flag riflettono il superamento delle soglie configurate <i>sul device</i>
     * (registri S4/S5/S6 statici, S7/S8 dinamici). Possono essere settati anche su
     * Msg #4 normali, non solo su Msg #8.
     *
     * @param data           payload TEK822
     * @param timestamp      timestamp di riferimento (base RTC del messaggio)
     */
    private List<Alarm> decodeDeviceAlarms(byte[] data, Instant timestamp) {
        List<Alarm> out = new ArrayList<>();
        int flags = data[ALARM_STATUS_OFFSET] & 0xFF;

        if ((flags & FLAG_LIMIT_1) != 0) {
            out.add(new Alarm(timestamp, AlarmCodes.ALARM_DEVICE_LIMIT_1,
                    "Device static limit 1 (S4) triggered"));
        }
        if ((flags & FLAG_LIMIT_2) != 0) {
            out.add(new Alarm(timestamp, AlarmCodes.ALARM_DEVICE_LIMIT_2,
                    "Device static limit 2 (S5) triggered"));
        }
        if ((flags & FLAG_LIMIT_3) != 0) {
            out.add(new Alarm(timestamp, AlarmCodes.ALARM_DEVICE_LIMIT_3,
                    "Device static limit 3 (S6) triggered"));
        }
        if ((flags & FLAG_BUND_STATUS) != 0) {
            out.add(new Alarm(timestamp, AlarmCodes.ALARM_DEVICE_BUND_STATUS,
                    "Bund switch state changed"));
        }
        return out;
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

    private DecodedPacket decodeSettings(byte[] data, Instant now) {
        String ascii = extractAsciiPayload(data);
        List<Measure> measures = new ArrayList<>();

        for (String setting : ascii.split(",")) {
            if (!setting.contains("=")) continue;
            String[] parts = setting.split("=", 2);
            String key      = "setting." + parts[0].trim();
            String rawValue = parts.length > 1 ? parts[1].trim() : "";

            // Strategia: emettiamo SEMPRE una Measure per ogni Sx trovato.
            //   - value = valore numerico parsato come HEX (PDF §3.20).
            //             Esempi: S0=80 → 0x80=128, S22=181A → 0x181A=6170.
            //   - unit  = la stringa ASCII originale, sempre preservata.
            //
            // Per i registri non-hex (S9 phone, S11 password, S12 APN, S15 IP,
            // settings vuoti, ecc.) il parse genera NumberFormatException:
            // teniamo value=0 e usiamo unit per non perdere l'informazione.
            // Questo evita la perdita di dati operativamente importanti
            // come l'APN e l'IP del server.
            double numericValue = 0.0;
            if (!rawValue.isEmpty()) {
                try {
                    numericValue = Long.parseLong(rawValue, 16);
                } catch (NumberFormatException e) {
                    log.debug("Setting ASCII (non-hex) preservato in unit — {}={}", key, rawValue);
                }
            }
            measures.add(new Measure(now, key, numericValue, rawValue));
        }

        log.debug("Msg type 6: parsati {} setting(s)", measures.size());
        return new DecodedPacket(measures, List.of());
    }

    // ================================================================
    //  Message type 16 — statistics (ASCII CSV)
    //  Format: ICCID,energyUsed,minTemp,maxTemp,msgCount,failCount,
    //          totalSendTime,maxSendTime,minSendTime,rssiTotal,rssiValidCount,rssiFailCount
    // ================================================================

    private DecodedPacket decodeStatistics(byte[] data, Instant now) {
        String ascii  = extractAsciiPayload(data);
        String[] flds = ascii.split(",");

        List<Measure> measures = new ArrayList<>();

        if (flds.length >= 12) {
            // ICCID: 20 cifre, non rappresentabili in double senza perdita di
            // precisione → la stringa raw viene preservata in unit (value=0).
            // Stesso pattern usato per i setting ASCII (S12 APN, S15 IP).
            measures.add(new Measure(now, "stats.iccid", 0.0, flds[0].trim()));

            safeAdd(measures, now, "stats.energy_used_ma_minutes", flds[1],  "mA·min");
            safeAdd(measures, now, "stats.min_temperature_c",      flds[2],  "°C");
            safeAdd(measures, now, "stats.max_temperature_c",      flds[3],  "°C");
            safeAdd(measures, now, "stats.message_count",          flds[4],  "");
            safeAdd(measures, now, "stats.delivery_fail",          flds[5],  "");
            safeAdd(measures, now, "stats.total_send_time_s",      flds[6],  "s");
            safeAdd(measures, now, "stats.max_send_time_s",        flds[7],  "s");
            safeAdd(measures, now, "stats.min_send_time_s",        flds[8],  "s");
            safeAdd(measures, now, "stats.rssi_total",             flds[9],  "");
            safeAdd(measures, now, "stats.rssi_valid_count",       flds[10], "");
            safeAdd(measures, now, "stats.rssi_fail_count",        flds[11], "");
            log.debug("Msg type 16: parsed {} stat(s)", measures.size());
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

    private DecodedPacket decodeGps(byte[] data, Instant now) {
        String ascii  = extractAsciiPayload(data);
        String[] flds = ascii.split(",");

        List<Measure> measures = new ArrayList<>();

        if (flds.length >= 12) {
            // Campi numerici: parse tramite safeAdd
            safeAdd(measures, now, "gps.time_to_fix_s", flds[0],  "s");
            safeAdd(measures, now, "gps.hdop",          flds[4],  "");
            safeAdd(measures, now, "gps.altitude_m",    flds[5],  "m");
            safeAdd(measures, now, "gps.fix_mode",      flds[6],  "");
            safeAdd(measures, now, "gps.heading_deg",   flds[7],  "°");
            safeAdd(measures, now, "gps.speed_kmh",     flds[8],  "km/h");
            safeAdd(measures, now, "gps.speed_knots",   flds[9],  "knots");
            safeAdd(measures, now, "gps.satellites",    flds[11], "");

            // Campi stringa preservati in unit (value=0): UTC, LAT NMEA, LON NMEA,
            // Date. Stesso pattern usato per ICCID in Msg #16 e setting ASCII in Msg #6.
            measures.add(new Measure(now, "gps.utc",       0.0, flds[1].trim()));
            measures.add(new Measure(now, "gps.latitude",  0.0, flds[2].trim()));
            measures.add(new Measure(now, "gps.longitude", 0.0, flds[3].trim()));
            measures.add(new Measure(now, "gps.date",      0.0, flds[10].trim()));

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
     * I byte a partire da ASCII_PAYLOAD_START (17) sono caratteri ASCII diretti
     * fino agli ultimi 2 byte, riservati al CRC (vedi XLSM sheet "822" celle G94/G95).
     * Rimuove la virgola iniziale se presente (alcuni campi iniziano con ',').
     */
    private String extractAsciiPayload(byte[] data) {
        int end = data.length - CRC_TRAILER_LEN;
        if (end <= ASCII_PAYLOAD_START) return "";
        String raw = new String(data, ASCII_PAYLOAD_START,
                end - ASCII_PAYLOAD_START, StandardCharsets.US_ASCII);
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
