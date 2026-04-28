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

    /**
     * S-command types: modificano registri di configurazione e richiedono
     * R3=ACTIVE (reboot) per applicare le modifiche (sezione 3.20 del manuale).
     */
    private static final Set<String> S_COMMAND_TYPES = Set.of(
            CMD_SET_INTERVAL, CMD_SET_LISTEN, CMD_SET_SCHEDULE,
            CMD_SET_ALARM_THRESHOLD, CMD_SET_APN, CMD_SET_SERVER);

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

}
