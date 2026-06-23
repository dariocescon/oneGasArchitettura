package com.aton.proj.gastelemetry.worker.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Encoder per comandi destinati a dispositivi Tekelek TEK822 e compatibili.
 *
 * Portato da: onGas_Meteor_claude → encoder/impl/Tek822Encoder.java
 * Basato su:  TEK 822 Logger NB-IoT/CAT-M1 User Manual (9-5988-07)
 *
 * Differenze rispetto all'originale:
 *  - Nessuna dipendenza da Spring (@Component rimosso) — classe utility pura
 *  - DeviceCommand sostituito da {@link CommandEntry}
 *  - EncodingException sostituita da IllegalArgumentException
 *  - encode() restituisce List<String> di stringhe <b>ASCII</b>
 *    (es. {@code "TEK822,S0=80"}), una per comando logico.
 *
 * <p><b>Nota sul wire format (fix step 5):</b> il manuale TEK822 §3.21 cita la
 * conversione "ASCII → HEX" solo nel contesto SMS / debugging. Su TCP/GPRS il
 * device riceve i byte ASCII del comando concatenato. La precedente versione
 * dell'encoder produceva stringhe HEX e il worker le inviava come tali,
 * trasmettendo letteralmente i caratteri "5","4","4","5",... invece dei byte
 * "T","E","K",... — il device le scartava silenziosamente.
 *
 * <p>La <b>composizione finale</b> (concatenazione + dedup password) è
 * responsabilità di {@code Tek822Worker}, non di questa classe. Vedi
 * {@code Tek822Worker.composeAsciiPayload()}.
 */
public class Tek822Encoder {

    private static final Logger log = LoggerFactory.getLogger(Tek822Encoder.class);

    // ---- Device types supportati ----
    public static final List<String> SUPPORTED_DEVICES = List.of(
            "TEK586", "TEK733", "TEK643", "TEK811",
            "TEK822V1", "TEK733A", "TEK871", "TEK811A",
            "TEK822V1BTN", "TEK822V2", "TEK900", "TEK880",
            "TEK898V2", "TEK898V1");

    // ---- Command types (sezione 3.20 e 3.21 del manuale) ----
    public static final String CMD_SET_INTERVAL            = "SET_INTERVAL";          // S0
    public static final String CMD_SET_LISTEN              = "SET_LISTEN";            // S1
    public static final String CMD_SET_SCHEDULE            = "SET_SCHEDULE";          // S2
    public static final String CMD_REBOOT                  = "REBOOT";               // R3=ACTIVE
    public static final String CMD_REQUEST_STATUS          = "REQUEST_STATUS";        // R6=02
    public static final String CMD_SET_ALARM_THRESHOLD     = "SET_ALARM_THRESHOLD";  // S4/S5/S6
    public static final String CMD_SHUTDOWN                = "SHUTDOWN";             // R1=80
    public static final String CMD_SET_RTC                 = "SET_RTC";              // R2
    public static final String CMD_DEACTIVATE              = "DEACTIVATE";           // R4=DEACT
    public static final String CMD_CLOSE_TCP               = "CLOSE_TCP";            // R6=03
    public static final String CMD_REQUEST_GPS             = "REQUEST_GPS";          // R7
    public static final String CMD_REQUEST_SETTINGS        = "REQUEST_SETTINGS";     // R1=02/04/08
    public static final String CMD_RESET_RTC               = "RESET_RTC";            // R1=10
    public static final String CMD_REQUEST_BUFFER_DATA     = "REQUEST_BUFFER_DATA";  // R1=20
    public static final String CMD_REQUEST_DIAGNOSTIC_DATA = "REQUEST_DIAGNOSTIC_DATA"; // R6=01
    public static final String CMD_SET_APN                 = "SET_APN";              // S12/S13/S14
    public static final String CMD_SET_SERVER              = "SET_SERVER";           // S15/S16

    // ---- S-register aggiuntivi (sezioni 3.20.4, 3.20.6, 3.20.7-16 del manuale) ----
    public static final String CMD_SET_CONTROL_CONFIG      = "SET_CONTROL_CONFIG";    // S3 (network mode + flags)
    public static final String CMD_SET_DYNAMIC_LIMIT_1     = "SET_DYNAMIC_LIMIT_1";   // S7
    public static final String CMD_SET_DYNAMIC_LIMIT_2     = "SET_DYNAMIC_LIMIT_2";   // S8
    public static final String CMD_SET_PASSWORD            = "SET_PASSWORD";          // S11
    public static final String CMD_SET_BATTERY_CAPACITY    = "SET_BATTERY_CAPACITY";  // S17
    public static final String CMD_SET_F_STOP              = "SET_F_STOP";            // S18
    public static final String CMD_SET_CONTROL3_CONFIG     = "SET_CONTROL3_CONFIG";   // S19 (APN auth)
    public static final String CMD_SET_E_STOP              = "SET_E_STOP";            // S20
    public static final String CMD_SET_MCC_MNC             = "SET_MCC_MNC";           // S21
    public static final String CMD_SET_LTE_BAND            = "SET_LTE_BAND";          // S22
    public static final String CMD_SET_RETRY_CONFIG        = "SET_RETRY_CONFIG";      // S23
    public static final String CMD_SET_SCHEDULE_DELAY      = "SET_SCHEDULE_DELAY";    // S24
    public static final String CMD_SET_CONTROL2_CONFIG     = "SET_CONTROL2_CONFIG";   // S26 (sensor format)
    public static final String CMD_SET_CONTROL4_CONFIG     = "SET_CONTROL4_CONFIG";   // S29

    /**
     * S-command types: modificano registri di configurazione e richiedono
     * R3=ACTIVE (reboot) per applicare le modifiche (sezione 3.20 del manuale).
     */
    private static final Set<String> S_COMMAND_TYPES = Set.of(
            CMD_SET_INTERVAL, CMD_SET_LISTEN, CMD_SET_SCHEDULE,
            CMD_SET_ALARM_THRESHOLD, CMD_SET_APN, CMD_SET_SERVER,
            CMD_SET_CONTROL_CONFIG, CMD_SET_DYNAMIC_LIMIT_1, CMD_SET_DYNAMIC_LIMIT_2,
            CMD_SET_PASSWORD, CMD_SET_BATTERY_CAPACITY,
            CMD_SET_F_STOP, CMD_SET_CONTROL3_CONFIG, CMD_SET_E_STOP,
            CMD_SET_MCC_MNC, CMD_SET_LTE_BAND,
            CMD_SET_RETRY_CONFIG, CMD_SET_SCHEDULE_DELAY,
            CMD_SET_CONTROL2_CONFIG, CMD_SET_CONTROL4_CONFIG);

    /** Password di default (sezione 3.9 del manuale) */
    private static final String DEFAULT_PASSWORD = "TEK822";

    // ================================================================
    //  Entry point pubblico
    // ================================================================

    /**
     * Codifica una lista di comandi in stringhe ASCII pronte per la composizione.
     *
     * <p>Ogni stringa ha la forma {@code "<password>,<register>=<value>"} (o, per
     * SET_APN/SET_SERVER, più registri separati da virgola). La concatenazione
     * finale e la rimozione delle password duplicate sono compito del Worker.
     *
     * <p>Auto-appende REBOOT (R3=ACTIVE) se la lista contiene S-commands e non
     * include già un REBOOT esplicito (sezione 3.20 del manuale TEK822).
     *
     * @param commands comandi da codificare (la lista non viene modificata)
     * @return lista ordinata di stringhe ASCII, una per comando
     */
    public List<String> encode(List<CommandEntry> commands) {
        List<CommandEntry> work = new ArrayList<>(commands); // copia mutabile
        ensureRebootIfNeeded(work);

        List<String> asciiCommands = new ArrayList<>();
        for (CommandEntry cmd : work) {
            String ascii = encodeCommand(cmd);
            asciiCommands.add(ascii);
            log.debug("Encoded {} → ASCII: {}", cmd.commandType(), ascii);
        }
        return asciiCommands;
    }

    // ================================================================
    //  Auto-append REBOOT
    // ================================================================

    /**
     * Se la lista contiene S-commands, verifica che sia presente anche un REBOOT.
     * Se mancante, ne aggiunge uno sintetico in coda.
     */
    private void ensureRebootIfNeeded(List<CommandEntry> commands) {
        boolean hasSCommand = commands.stream()
                .anyMatch(c -> S_COMMAND_TYPES.contains(c.commandType()));
        if (!hasSCommand) return;

        boolean hasReboot = commands.stream()
                .anyMatch(c -> CMD_REBOOT.equals(c.commandType()));
        if (hasReboot) {
            log.debug("S-commands presenti con REBOOT esplicito — nessun auto-append");
            return;
        }

        // Estrai password dal primo S-command
        String password = commands.stream()
                .filter(c -> S_COMMAND_TYPES.contains(c.commandType()))
                .findFirst()
                .map(c -> c.getParamOrDefault("password", DEFAULT_PASSWORD).toString())
                .orElse(DEFAULT_PASSWORD);

        CommandEntry first = commands.get(0);
        CommandEntry reboot = new CommandEntry(first.deviceId(), first.deviceType(), CMD_REBOOT);
        reboot.parameters().put("password", password);
        commands.add(reboot);

        log.info("Auto-appended REBOOT (R3=ACTIVE) perché sono presenti S-commands");
    }

    // ================================================================
    //  Encoding per tipo di comando
    // ================================================================

    private String encodeCommand(CommandEntry cmd) {
        String password = cmd.getParamOrDefault("password", DEFAULT_PASSWORD).toString();
        return switch (cmd.commandType()) {
            case CMD_SET_INTERVAL            -> encodeSetInterval(password, cmd);
            case CMD_SET_LISTEN              -> encodeSetListen(password, cmd);
            case CMD_SET_SCHEDULE            -> encodeSetSchedule(password, cmd);
            case CMD_REBOOT                  -> encodeReboot(password);
            case CMD_REQUEST_STATUS          -> encodeRequestStatus(password);
            case CMD_SET_ALARM_THRESHOLD     -> encodeSetAlarmThreshold(password, cmd);
            case CMD_SHUTDOWN                -> encodeShutdown(password);
            case CMD_SET_RTC                 -> encodeSetRTC(password);
            case CMD_DEACTIVATE              -> encodeDeactivate(password);
            case CMD_CLOSE_TCP               -> encodeCloseTCP(password);
            case CMD_REQUEST_GPS             -> encodeRequestGPS(password, cmd);
            case CMD_REQUEST_SETTINGS        -> encodeRequestSettings(password, cmd);
            case CMD_RESET_RTC               -> encodeResetRtc(password);
            case CMD_REQUEST_BUFFER_DATA     -> encodeRequestBufferData(password);
            case CMD_REQUEST_DIAGNOSTIC_DATA -> encodeRequestDiagnosticData(password);
            case CMD_SET_APN                 -> encodeSetAPN(password, cmd);
            case CMD_SET_SERVER              -> encodeSetServer(password, cmd);
            case CMD_SET_CONTROL_CONFIG      -> encodeSetControlConfig(password, cmd);
            case CMD_SET_DYNAMIC_LIMIT_1     -> encodeSetDynamicLimit(password, cmd, "S7");
            case CMD_SET_DYNAMIC_LIMIT_2     -> encodeSetDynamicLimit(password, cmd, "S8");
            case CMD_SET_PASSWORD            -> encodeSetPassword(password, cmd);
            case CMD_SET_BATTERY_CAPACITY    -> encodeSetBatteryCapacity(password, cmd);
            case CMD_SET_F_STOP              -> encodeSetFStop(password, cmd);
            case CMD_SET_CONTROL3_CONFIG     -> encodeSetControl3Config(password, cmd);
            case CMD_SET_E_STOP              -> encodeSetEStop(password, cmd);
            case CMD_SET_MCC_MNC             -> encodeSetMccMnc(password, cmd);
            case CMD_SET_LTE_BAND            -> encodeSetLteBand(password, cmd);
            case CMD_SET_RETRY_CONFIG        -> encodeSetRetryConfig(password, cmd);
            case CMD_SET_SCHEDULE_DELAY      -> encodeSetScheduleDelay(password, cmd);
            case CMD_SET_CONTROL2_CONFIG     -> encodeSetControl2Config(password, cmd);
            case CMD_SET_CONTROL4_CONFIG     -> encodeSetControl4Config(password, cmd);
            default -> throw new IllegalArgumentException("Command type sconosciuto: " + cmd.commandType());
        };
    }

    // ---- S0: Logger Configuration (sezione 3.20.1) ----
    // Formula: S0 = (128 × B) + (A × 4)
    //   A = logger speed in hours (incrementi di 0.25)
    //   B = sampling period (0=1min, 1=15min)
    private String encodeSetInterval(String pw, CommandEntry cmd) {
        double loggerSpeed  = Double.parseDouble(cmd.getParam("interval").toString());
        int samplingPeriod  = Integer.parseInt(cmd.getParamOrDefault("samplingPeriod", "1").toString());
        int s0Value         = (int) ((128 * samplingPeriod) + (loggerSpeed * 4));
        return String.format("%s,S0=%02X", pw, s0Value);
    }

    // ---- S1: Listen Configuration (sezione 3.20.2) ----
    // Formula: S1 = listenMinutes / 5
    private String encodeSetListen(String pw, CommandEntry cmd) {
        int listenMinutes = Integer.parseInt(cmd.getParam("listenMinutes").toString());
        return String.format("%s,S1=%02X", pw, listenMinutes / 5);
    }

    // ---- S2: Schedule Configuration (sezione 3.20.3) ----
    // Default 7F2000: tutti i giorni (0x7F), ore 8:00 (0x20 = 32×15min), once daily (0x00)
    private String encodeSetSchedule(String pw, CommandEntry cmd) {
        String schedule = cmd.getParamOrDefault("schedule", "7F2000").toString();
        return String.format("%s,S2=%s", pw, schedule);
    }

    // ---- R3=ACTIVE: Reboot/Activate (sezione 3.21) ----
    private String encodeReboot(String pw) {
        return String.format("%s,R3=ACTIVE", pw);
    }

    // ---- R6=02: Request Status / Message Type 16 (sezione 3.21) ----
    private String encodeRequestStatus(String pw) {
        return String.format("%s,R6=02", pw);
    }

    // ---- S4/S5/S6: Static Alarm Configuration (sezione 3.20.5) ----
    // Formula: S4 = D + C×(2^10) + B×(2^14) + A×(2^15)
    //   D = threshold, C = hysteresis, B = enabled, A = polarity
    private String encodeSetAlarmThreshold(String pw, CommandEntry cmd) {
        int threshold   = Integer.parseInt(cmd.getParam("threshold").toString());
        int hysteresis  = Integer.parseInt(cmd.getParamOrDefault("hysteresis", "10").toString());
        boolean enabled = Boolean.parseBoolean(cmd.getParamOrDefault("enabled", "true").toString());
        boolean polarity = Boolean.parseBoolean(cmd.getParamOrDefault("polarity", "true").toString());
        String alarmReg = cmd.getParamOrDefault("alarmRegister", "S4").toString();
        int value = threshold
                + (hysteresis * (1 << 10))
                + ((enabled  ? 1 : 0) * (1 << 14))
                + ((polarity ? 1 : 0) * (1 << 15));
        return String.format("%s,%s=%04X", pw, alarmReg, value);
    }

    // ---- R1=80: Shutdown modem (sezione 3.21) ----
    private String encodeShutdown(String pw) {
        return String.format("%s,R1=80", pw);
    }

    // ---- R2: Set RTC al timestamp corrente (sezione 3.21) ----
    // Format: yy/MM/dd:HH/mm/ss
    private String encodeSetRTC(String pw) {
        String dt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yy/MM/dd:HH/mm/ss"));
        return String.format("%s,R2=%s", pw, dt);
    }

    // ---- R4=DEACT: Deactivate scheduled uploads (sezione 3.21) ----
    private String encodeDeactivate(String pw) {
        return String.format("%s,R4=DEACT", pw);
    }

    // ---- R6=03: Close TCP connection (sezione 3.21) ----
    private String encodeCloseTCP(String pw) {
        return String.format("%s,R6=03", pw);
    }

    // ---- R7=XX: Request GPS (sezione 3.21) — timeout in hex ----
    private String encodeRequestGPS(String pw, CommandEntry cmd) {
        int timeout = Integer.parseInt(cmd.getParamOrDefault("timeout", "60").toString());
        return String.format("%s,R7=%02X", pw, timeout);
    }

    // ---- R1=02/04/08: Request Settings / Message Type 6 (sezione 3.21) ----
    private String encodeRequestSettings(String pw, CommandEntry cmd) {
        String startFrom = cmd.getParamOrDefault("startFrom", "S0").toString();
        String r1 = switch (startFrom) {
            case "S0"  -> "02"; // tutti i registri da S0
            case "S12" -> "04"; // da S12 (connettività)
            case "S19" -> "08"; // da S19
            default    -> "02";
        };
        return String.format("%s,R1=%s", pw, r1);
    }

    // ---- R1=10: Reset RTC (CF-5018-20) ----
    private String encodeResetRtc(String pw) {
        return String.format("%s,R1=10", pw);
    }

    // ---- R1=20: Request Buffer Data (CF-5018-20) ----
    // Forza invio dati bufferizzati (risposta: Msg#8, 10 misurazioni recenti)
    private String encodeRequestBufferData(String pw) {
        return String.format("%s,R1=20", pw);
    }

    // ---- R6=01: Request Diagnostic Data (CF-5018-20) ----
    private String encodeRequestDiagnosticData(String pw) {
        return String.format("%s,R6=01", pw);
    }

    // ---- S12/S13/S14: Set APN (sezione 3.20.7) ----
    private String encodeSetAPN(String pw, CommandEntry cmd) {
        String apn  = cmd.getParam("apn").toString();
        String user = cmd.getParamOrDefault("username", "").toString();
        String pass = cmd.getParamOrDefault("apnPassword", "").toString();
        return String.format("%s,S12=%s,S13=%s,S14=%s", pw, apn, user, pass);
    }

    // ---- S15/S16: Set Server (sezione 3.20.7) ----
    private String encodeSetServer(String pw, CommandEntry cmd) {
        String ip   = cmd.getParam("serverIp").toString();
        String port = cmd.getParam("serverPort").toString();
        return String.format("%s,S15=%s,S16=%s", pw, ip, port);
    }

    // ---- S3: Control Configurator (sezione 3.20.4) ----
    // Bitmask: bits[1:0] = Network Mode (0=LTE CatM1, 1=NB-IoT, 2=2G, 3=Auto);
    //          bit 2 = Send Msg #16 monthly; bit 3 = reserved;
    //          bit 4 = Dry Contact P1; bit 5 = CRC checking; bit 6 = Delivery Report;
    //          bit 7 = Verbose TSP.
    // Esempio XLSM: networkMode=3 (Auto) + verboseTSP=1 → S3 = 0b10000011 = 0x83
    private String encodeSetControlConfig(String pw, CommandEntry cmd) {
        int networkMode = Integer.parseInt(cmd.getParamOrDefault("networkMode", "0").toString()) & 0x03;
        boolean sendMsg16Monthly = Boolean.parseBoolean(cmd.getParamOrDefault("sendMsg16Monthly", "false").toString());
        boolean dryContactP1     = Boolean.parseBoolean(cmd.getParamOrDefault("dryContactP1",     "false").toString());
        boolean crcEnabled       = Boolean.parseBoolean(cmd.getParamOrDefault("crcEnabled",       "false").toString());
        boolean deliveryReport   = Boolean.parseBoolean(cmd.getParamOrDefault("deliveryReport",   "false").toString());
        boolean verboseTsp       = Boolean.parseBoolean(cmd.getParamOrDefault("verboseTsp",       "false").toString());
        int value = networkMode
                | ((sendMsg16Monthly ? 1 : 0) << 2)
                | ((dryContactP1     ? 1 : 0) << 4)
                | ((crcEnabled       ? 1 : 0) << 5)
                | ((deliveryReport   ? 1 : 0) << 6)
                | ((verboseTsp       ? 1 : 0) << 7);
        return String.format("%s,S3=%02X", pw, value);
    }

    // ---- S7/S8: Dynamic Limit (sezione 3.20.6) ----
    // Formula: S7 = (polarity << 7) | (enabled << 6) | (rate & 0x3F)
    //   - polarity: 1 = alarm if rising, 0 = alarm if falling
    //   - enabled: 1 = abilita allarme dinamico
    //   - rate: 0-63 units/min o /15min in base a S0 bit 7
    // S8 può anche operare come No-Change Alarm (NCA) a seconda di S26 bit 4.
    private String encodeSetDynamicLimit(String pw, CommandEntry cmd, String register) {
        boolean polarity = Boolean.parseBoolean(cmd.getParamOrDefault("polarity", "false").toString());
        boolean enabled  = Boolean.parseBoolean(cmd.getParamOrDefault("enabled",  "false").toString());
        int rate         = Integer.parseInt(cmd.getParamOrDefault("rate", "0").toString()) & 0x3F;
        int value = ((polarity ? 1 : 0) << 7) | ((enabled ? 1 : 0) << 6) | rate;
        return String.format("%s,%s=%02X", pw, register, value);
    }

    // ---- S11: Unit Password (sezione 3.9) ----
    // ASCII max 6 caratteri. Permette di ruotare la password che il device richiede
    // in testa ai comandi (cfr. composeAsciiPayload nel Worker).
    private String encodeSetPassword(String pw, CommandEntry cmd) {
        String newPwd = cmd.getParam("newPassword").toString();
        return String.format("%s,S11=%s", pw, newPwd);
    }

    // ---- S17: Battery Capacity (sezione 3.10) ----
    // Capacità in mAh, codificata come hex 4 cifre. Esempi: 3600="0E10", 7200="1C20",
    // 7700="1E14", 17500="445C".
    private String encodeSetBatteryCapacity(String pw, CommandEntry cmd) {
        int capacityMah = Integer.parseInt(cmd.getParam("capacityMah").toString());
        return String.format("%s,S17=%04X", pw, capacityMah);
    }

    // ---- S18: F-Stop (sezione 3.20.8) — calibrazione ratiometrica Max ----
    // Formula: S18 = round((200 × voltage) / 5)
    // Esempio: 5V → S18 = 200 = 0xC8
    private String encodeSetFStop(String pw, CommandEntry cmd) {
        double voltage = Double.parseDouble(cmd.getParam("voltage").toString());
        int value = (int) Math.round((200.0 * voltage) / 5.0);
        return String.format("%s,S18=%02X", pw, value);
    }

    // ---- S19: Control3 Configurator (sezione 3.20.9) ----
    // bit 0: APN Auth Type (0 = PAP or CHAP, 1 = None)
    // bit 1: Inhibit background beeping
    // bit 2: Fallback to GPRS from LTE
    // bits 3-7: reserved
    private String encodeSetControl3Config(String pw, CommandEntry cmd) {
        boolean apnAuthNone   = Boolean.parseBoolean(cmd.getParamOrDefault("apnAuthNone",   "false").toString());
        boolean inhibitBeep   = Boolean.parseBoolean(cmd.getParamOrDefault("inhibitBeep",   "false").toString());
        boolean fallbackGprs  = Boolean.parseBoolean(cmd.getParamOrDefault("fallbackGprs",  "false").toString());
        int value = ((apnAuthNone  ? 1 : 0))
                | ((inhibitBeep   ? 1 : 0) << 1)
                | ((fallbackGprs  ? 1 : 0) << 2);
        return String.format("%s,S19=%02X", pw, value);
    }

    // ---- S20: E-Stop (sezione 3.20.10) — calibrazione ratiometrica Min ----
    // Formula: S20 = round((200 × voltage) / 5)
    // Esempio: 1V → S20 = 40 = 0x28
    private String encodeSetEStop(String pw, CommandEntry cmd) {
        double voltage = Double.parseDouble(cmd.getParam("voltage").toString());
        int value = (int) Math.round((200.0 * voltage) / 5.0);
        return String.format("%s,S20=%02X", pw, value);
    }

    // ---- S21: MCC_MNC operatore (sezione 3.20.11) ----
    // ASCII, es. "27201" per Vodafone IE. Lascia vuoto per 2G.
    private String encodeSetMccMnc(String pw, CommandEntry cmd) {
        String mccMnc = cmd.getParamOrDefault("mccMnc", "").toString();
        return String.format("%s,S21=%s", pw, mccMnc);
    }

    // ---- S22: LTE Band (sezione 3.20.12) ----
    // Codice hex della banda. Tabella PDF: B1=1, B2=2, B3=4, B4=8, B5=10, B8=80,
    // B12=800, B13=1000, B18=20000, B19=40000, B20=80000, B26=2000000,
    // B28=8000000, B39=4000000000.
    private String encodeSetLteBand(String pw, CommandEntry cmd) {
        // bandCode è un long perché B39 = 4_000_000_000 > Integer.MAX_VALUE
        long bandCode = Long.parseLong(cmd.getParam("bandCode").toString());
        return String.format("%s,S22=%X", pw, bandCode);
    }

    // ---- S23: Message Deliver Configuration (sezione 3.20.13) ----
    // Formula: X = tryTickets - 1; Y = (periodSec / 10) - 1; S23 = X + (Y × 8)
    // Esempio: tryTickets=4, periodSec=30 → X=3, Y=2 → S23 = 3 + 16 = 19 = 0x13
    private String encodeSetRetryConfig(String pw, CommandEntry cmd) {
        int tryTickets = Integer.parseInt(cmd.getParam("tryTickets").toString());
        int periodSec  = Integer.parseInt(cmd.getParam("periodSec").toString());
        int x = tryTickets - 1;
        int y = (periodSec / 10) - 1;
        int value = x + (y * 8);
        return String.format("%s,S23=%02X", pw, value);
    }

    // ---- S24: Schedule Delay (sezione 3.20.14) ----
    // Ritardo in minuti dal trigger di S2, max 14. Permette di spalmare il traffico
    // di più device che condividono lo stesso schedule.
    private String encodeSetScheduleDelay(String pw, CommandEntry cmd) {
        int delayMinutes = Integer.parseInt(cmd.getParam("delayMinutes").toString());
        return String.format("%s,S24=%02X", pw, delayMinutes);
    }

    // ---- S26: Control2 Configurator (sezione 3.20.15) ----
    // **Critico** per interpretare le misure (bits 5/6/7 definiscono unità ADC).
    // bit 0: Enable Temperature Alarm
    // bit 1: SMS Disable fallback (GPRS only)
    // bit 2: SMS Alarm only fallback
    // bit 3: Reserved
    // bit 4: For No Change Alarm Set / DynLim2 Alarm Clear
    // bit 5: ADC Sensor1 — 0=Normal, 1=Inverted output
    // bit 6: ADC Sensor1 — 0=Low Res 0-100%, 1=High Res 0-1000
    // bit 7: ADC Sensor1 — 0=usa bit 5/6, 1=Raw 10-bit ADC 0-1023
    private String encodeSetControl2Config(String pw, CommandEntry cmd) {
        boolean tempAlarm     = Boolean.parseBoolean(cmd.getParamOrDefault("temperatureAlarm",   "false").toString());
        boolean smsDisable    = Boolean.parseBoolean(cmd.getParamOrDefault("smsDisableFallback", "false").toString());
        boolean smsAlarmOnly  = Boolean.parseBoolean(cmd.getParamOrDefault("smsAlarmOnly",       "false").toString());
        boolean noChangeAlarm = Boolean.parseBoolean(cmd.getParamOrDefault("noChangeAlarm",      "false").toString());
        boolean adcInverted   = Boolean.parseBoolean(cmd.getParamOrDefault("adcInverted",        "false").toString());
        boolean adcHighRes    = Boolean.parseBoolean(cmd.getParamOrDefault("adcHighRes",         "false").toString());
        boolean adcRaw        = Boolean.parseBoolean(cmd.getParamOrDefault("adcRaw",             "false").toString());
        int value = ((tempAlarm     ? 1 : 0))
                | ((smsDisable    ? 1 : 0) << 1)
                | ((smsAlarmOnly  ? 1 : 0) << 2)
                | ((noChangeAlarm ? 1 : 0) << 4)
                | ((adcInverted   ? 1 : 0) << 5)
                | ((adcHighRes    ? 1 : 0) << 6)
                | ((adcRaw        ? 1 : 0) << 7);
        return String.format("%s,S26=%02X", pw, value);
    }

    // ---- S29: Control4 Configurator (sezione 3.20.16) ----
    // bits 0-2: reserved
    // bit 3: Long timeframe retry disabilitato (0=abilitato, 1=disabilitato)
    // bits 4-5: Dynamic Limit 1 Offset Timer (0=disabled, 1=15min, 2=30min, 3=60min)
    // bits 6-7: reserved
    private String encodeSetControl4Config(String pw, CommandEntry cmd) {
        boolean disableLongRetry = Boolean.parseBoolean(cmd.getParamOrDefault("disableLongRetry", "false").toString());
        int dynLim1OffsetTimer   = Integer.parseInt(cmd.getParamOrDefault("dynLim1OffsetTimer", "0").toString()) & 0x03;
        int value = ((disableLongRetry ? 1 : 0) << 3)
                | (dynLim1OffsetTimer << 4);
        return String.format("%s,S29=%02X", pw, value);
    }

}
