package com.aton.proj.gastelemetry.worker.impl;

import com.aton.proj.gastelemetry.common.CommandsPacket;
import com.aton.proj.gastelemetry.common.Worker;
import com.aton.proj.gastelemetry.common.WorkerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Implementazione Worker per la famiglia di device TEK822 (Tekelek).
 *
 * Logica portata da: onGas_Meteor_claude → TcpConnectionHandler + TelemetryService (parte comandi)
 *
 * Struttura header TEK822 (rilevante per il Worker):
 *   Byte  0     : product type
 *   Byte  7-14  : IMEI — 8 byte BCD → 16 nibble → 15 cifre (leading zero rimosso)
 *   Byte 15     : bits[5:0]=msgType, bits[7:6]=length hi
 *   Byte 16     : length lo
 */
@Component
public class Tek822Worker implements Worker {

    private static final Logger log = LoggerFactory.getLogger(Tek822Worker.class);

    // ---- IMEI: bytes 7-14 (BCD, 8 byte) ----
    private static final int IMEI_START = 7;
    private static final int IMEI_END   = 15; // esclusivo

    // ---- Lunghezza minima per leggere il campo length ----
    private static final int MIN_HEADER_LEN = 17;

    // ---- Product type supportati (da TekMessageDecoder.decodeProductType) ----
    private static final Set<Integer> SUPPORTED_PRODUCT_TYPES = Set.of(
             2,  // TEK586
             5,  // TEK733
             6,  // TEK643
             7,  // TEK811
             8,  // TEK822V1
             9,  // TEK733A
            10,  // TEK871
            11,  // TEK811A
            23,  // TEK822V1BTN
            24,  // TEK822V2
            25,  // TEK900
            26,  // TEK880
            27,  // TEK898V2
            28   // TEK898V1
    );

    @Override
    public boolean validate(byte[] data) {
        if (data == null || data.length < MIN_HEADER_LEN) {
            log.warn("Payload null o troppo corto: {} byte", data == null ? 0 : data.length);
            return false;
        }

        // Verifica product type
        int productType = data[0] & 0xFF;
        if (!SUPPORTED_PRODUCT_TYPES.contains(productType)) {
            log.warn("Product type non supportato: {}", productType);
            return false;
        }

        // Verifica lunghezza dichiarata nel payload
        int declaredLength = ((data[15] >> 6) & 0x03) * 256 + (data[16] & 0xFF);
        if (data.length < MIN_HEADER_LEN + declaredLength) {
            log.warn("Payload troncato: attesi {} byte, ricevuti {}",
                    MIN_HEADER_LEN + declaredLength, data.length);
            return false;
        }

        return true;
    }

    @Override
    public void doWork(WorkerContext ctx, byte[] data) {
        String deviceId = extractDeviceId(data);
        log.debug("Processing packet from device {}", deviceId);

        // TODO: decriptare data se il protocollo lo richiede
        // byte[] clearData = decrypt(data);

        ctx.publishPrimaryData(deviceId, data);

        CommandsPacket commands = ctx.getCommands(deviceId);
        if (commands.hasCommands()) {
            byte[] payload = composeAsciiPayload(commands);
            ctx.sendToDevice(payload); // TODO : spostare nel worker
            ctx.markCommandsSent(commands.handle());
            log.debug("Sent {} command(s) ({} byte) to device {}",
                    commands.commands().length, payload.length, deviceId);
        }
    }

    /**
     * Estrae il device ID (IMEI) dal payload.
     *
     * L'IMEI è codificato BCD nei byte 7-14 (8 byte → 16 nibble).
     * Il primo nibble è sempre 0 (leading zero del formato IMEI) e viene rimosso.
     * Risultato: stringa di 15 cifre decimali.
     *
     * Portato da: TekMessageDecoder.decodeImei() (onGas_Meteor_claude)
     */
    private String extractDeviceId(byte[] data) {
        StringBuilder imei = new StringBuilder(16);
        for (int i = IMEI_START; i < IMEI_END; i++) {
            imei.append((data[i] >> 4) & 0x0F);
            imei.append(data[i] & 0x0F);
        }
        return imei.substring(1); // rimuovi leading zero
    }

    /**
     * Compone il payload ASCII finale da inviare al device, concatenando i comandi
     * con virgole e rimuovendo la password duplicata (regola TEK822 §3.21).
     *
     * <p>Le regole — portate da {@code onGas_Meteor_claude.ControllerUtils.concatenateCommands}:
     * <ul>
     *   <li>Il primo comando mantiene la password (es. {@code "TEK822,S0=80"})</li>
     *   <li>I successivi vengono troncati al primo {@code ','}: tutto ciò che precede
     *       (la password) viene rimosso (es. {@code "TEK822,S1=01"} → {@code "S1=01"})</li>
     *   <li>I pezzi vengono uniti da virgole; il risultato è inviato come byte US-ASCII</li>
     * </ul>
     *
     * <p>Esempio: due comandi {@code "TEK822,S0=80"} + {@code "TEK822,R3=ACTIVE"}
     * → payload {@code "TEK822,S0=80,R3=ACTIVE"} (22 byte ASCII).
     *
     * <p>Package-private per consentire ai test di verificarlo direttamente.
     */
    public static byte[] composeAsciiPayload(CommandsPacket commands) {
        String[] ascii = commands.commands();
        if (ascii == null || ascii.length == 0) return new byte[0];

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ascii.length; i++) {
            if (i == 0) {
                sb.append(ascii[i]);                          // primo: password inclusa
            } else {
                sb.append(',').append(stripPassword(ascii[i])); // successivi: dedup password
            }
        }
        // Terminatore CRLF (fix B3): XLSM "822 CC" R0002 definisce la forma
        // canonica come "<Password>,<Settings>,<CRC><CRLF>". Senza il terminatore
        // alcuni firmware restano in listen mode in attesa della fine del comando.
        sb.append("\r\n");
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Rimuove il prefisso fino alla prima virgola inclusa ({@code "TEK822,S0=80"}
     * → {@code "S0=80"}). Se non c'è virgola, restituisce la stringa invariata.
     */
    private static String stripPassword(String asciiCommand) {
        int comma = asciiCommand.indexOf(',');
        if (comma < 0 || comma >= asciiCommand.length() - 1) return asciiCommand;
        return asciiCommand.substring(comma + 1);
    }
}
