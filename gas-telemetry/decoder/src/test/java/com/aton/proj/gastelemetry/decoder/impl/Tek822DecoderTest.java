package com.aton.proj.gastelemetry.decoder.impl;

import com.aton.proj.gastelemetry.common.Alarm;
import com.aton.proj.gastelemetry.common.DecodedPacket;
import com.aton.proj.gastelemetry.common.DecoderContext;
import com.aton.proj.gastelemetry.common.Measure;
import com.aton.proj.gastelemetry.decoder.alarm.AlarmCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario di {@link Tek822Decoder} con focus sulle fix B1/B2 segnalate
 * nel confronto col file di specifica {@code configuration_and_command_v1.21}.
 *
 * Coperture:
 *  - B1: i registri S in Msg #6 sono codificati in ESADECIMALE (PDF §3.20).
 *        S0=80 → 0x80=128. S22=181A → 0x181A=6170. Stringhe ASCII (APN, IP, password)
 *        non sono parsabili come hex e vengono saltate.
 *  - B2: la temperatura usa il byte unsigned intero, non mascherato a 0x7F.
 *        Byte 0x5B → 15.5°C (sample PDF §2.2.2.2). Byte 0xFF → 97.5°C.
 *        Byte 0x80 → 64°C (con maschera errata il decoder restituiva -30°C).
 */
class Tek822DecoderTest {

    private static final String DEVICE_ID = "TEST_DEVICE";

    private DecoderContext ctx;
    private Tek822Decoder  decoder;

    @BeforeEach
    void setUp() {
        ctx     = mock(DecoderContext.class);
        when(ctx.<Object>getConfig(anyString(), anyString())).thenReturn(null);
        when(ctx.<Object>getConfig(anyString())).thenReturn(null);
        decoder = new Tek822Decoder();
    }

    // ================================================================
    //  B1 — Parsing hex dei settings Msg #6
    // ================================================================

    @Test
    @DisplayName("B1 — Msg #6: S0=80 viene letto come 0x80 = 128 (non come decimale 80)")
    void msg6_parsesHexLoggerConfig() {
        byte[] payload = buildSettingsPayload("S0=80");

        DecodedPacket pkt = decode(payload);

        Measure s0 = findMeasure(pkt, "setting.S0");
        assertThat(s0.value()).isEqualTo(128.0);
    }

    @Test
    @DisplayName("B1 — Msg #6: S22=181A viene letto come 0x181A = 6170")
    void msg6_parsesMultiByteHexBand() {
        byte[] payload = buildSettingsPayload("S22=181A");

        DecodedPacket pkt = decode(payload);

        Measure s22 = findMeasure(pkt, "setting.S22");
        assertThat(s22.value()).isEqualTo(6170.0);
    }

    @Test
    @DisplayName("B1 — Msg #6: S2=7F2000 (schedule 3-byte) viene letto come 0x7F2000 = 8331264")
    void msg6_parsesThreeByteHexSchedule() {
        byte[] payload = buildSettingsPayload("S2=7F2000");

        DecodedPacket pkt = decode(payload);

        Measure s2 = findMeasure(pkt, "setting.S2");
        assertThat(s2.value()).isEqualTo(8331264.0);
    }

    @Test
    @DisplayName("B1 — Msg #6: registri ASCII (APN/IP/password) preservati con stringa raw in unit")
    void msg6_preservesAsciiRegisters() {
        byte[] payload = buildSettingsPayload(
                "S0=80,S11=TEK822,S12=stream.co.uk,S15=84.51.250.104,S22=181A");

        DecodedPacket pkt = decode(payload);

        // TUTTI i 5 setting devono essere presenti (no perdita di info)
        List<Measure> settings = pkt.measures().stream()
                .filter(m -> m.obisCode().startsWith("setting."))
                .toList();
        assertThat(settings).extracting(Measure::obisCode)
                .containsExactlyInAnyOrder(
                        "setting.S0", "setting.S11", "setting.S12",
                        "setting.S15", "setting.S22");

        // Hex-parseable: value = decimal, unit = raw hex string
        Measure s0 = findMeasure(pkt, "setting.S0");
        assertThat(s0.value()).isEqualTo(128.0);
        assertThat(s0.unit()).isEqualTo("80");

        Measure s22 = findMeasure(pkt, "setting.S22");
        assertThat(s22.value()).isEqualTo(6170.0);
        assertThat(s22.unit()).isEqualTo("181A");

        // ASCII non-hex: value = 0, unit = stringa ASCII originale
        Measure s11 = findMeasure(pkt, "setting.S11");
        assertThat(s11.value()).isEqualTo(0.0);
        assertThat(s11.unit()).isEqualTo("TEK822");

        Measure s12 = findMeasure(pkt, "setting.S12");
        assertThat(s12.value()).isEqualTo(0.0);
        assertThat(s12.unit()).isEqualTo("stream.co.uk");

        Measure s15 = findMeasure(pkt, "setting.S15");
        assertThat(s15.value()).isEqualTo(0.0);
        assertThat(s15.unit()).isEqualTo("84.51.250.104");
    }

    @Test
    @DisplayName("B1 — Msg #6: setting con valore vuoto (S9=) preservato con unit empty")
    void msg6_preservesEmptyValue() {
        byte[] payload = buildSettingsPayload("S0=80,S9=,S22=181A");

        DecodedPacket pkt = decode(payload);

        List<Measure> settings = pkt.measures().stream()
                .filter(m -> m.obisCode().startsWith("setting."))
                .toList();
        // Anche S9= (vuoto) è preservato
        assertThat(settings).extracting(Measure::obisCode)
                .containsExactlyInAnyOrder("setting.S0", "setting.S9", "setting.S22");

        Measure s9 = findMeasure(pkt, "setting.S9");
        assertThat(s9.value()).isEqualTo(0.0);
        assertThat(s9.unit()).isEqualTo("");
    }

    // ================================================================
    //  B2 — Temperatura usa il byte unsigned intero
    // ================================================================

    @Test
    @DisplayName("B2 — Temperatura: byte 0x5B → 15.5°C (esempio PDF §2.2.2.2)")
    void temperature_pdfSample() {
        // 1 misura: aux2=0x0A, temp=0x5B, aux1=0x28, dist_lo=0x77
        byte[] payload = buildMeasurePayload(new byte[]{ 0x0A, 0x5B, 0x28, 0x77 });

        DecodedPacket pkt = decode(payload);

        Measure temp = findMeasure(pkt, "temperature_c");
        assertThat(temp.value()).isEqualTo(15.5, within(0.001));
    }

    @Test
    @DisplayName("B2 — Temperatura: byte 0xFF → 97.5°C (massimo nominal range)")
    void temperature_highByteNoMaskClipping() {
        byte[] payload = buildMeasurePayload(new byte[]{ 0x0A, (byte) 0xFF, 0x28, 0x77 });

        DecodedPacket pkt = decode(payload);

        Measure temp = findMeasure(pkt, "temperature_c");
        // Con la vecchia maschera 0x7F: 0xFF & 0x7F = 0x7F = 127 → 33.5°C (errore di 64°C)
        assertThat(temp.value()).isEqualTo(97.5, within(0.001));
    }

    @Test
    @DisplayName("B2 — Temperatura: byte 0x80 → 34°C (vecchia maschera lo riduceva a -30°C)")
    void temperature_msbSetNoMaskClipping() {
        byte[] payload = buildMeasurePayload(new byte[]{ 0x0A, (byte) 0x80, 0x28, 0x77 });

        DecodedPacket pkt = decode(payload);

        Measure temp = findMeasure(pkt, "temperature_c");
        // Calcolo corretto: 0x80=128, 128/2=64, 64-30 = 34°C
        // Con la vecchia maschera 0x7F: 0x80 & 0x7F = 0 → 0/2-30 = -30°C (errore di 64°C)
        assertThat(temp.value()).isEqualTo(34.0, within(0.001));
    }

    @Test
    @DisplayName("B2 — Temperatura: byte 0x3C → 0°C (verifica offset -30 base)")
    void temperature_zeroCelsius() {
        byte[] payload = buildMeasurePayload(new byte[]{ 0x0A, 0x3C, 0x28, 0x77 });

        DecodedPacket pkt = decode(payload);

        Measure temp = findMeasure(pkt, "temperature_c");
        assertThat(temp.value()).isEqualTo(0.0, within(0.001));
    }

    // ================================================================
    //  Sanity: la sezione misure (distanza/aux) non è impattata dalle fix
    // ================================================================

    @Test
    @DisplayName("Sanity — distanza 10-bit dall'esempio PDF (0x2877 → 119) resta corretta")
    void distance_pdfSampleStillCorrect() {
        byte[] payload = buildMeasurePayload(new byte[]{ 0x0A, 0x5B, 0x28, 0x77 });

        DecodedPacket pkt = decode(payload);

        Measure dist = findMeasure(pkt, "distance_cm");
        // bit[1:0] di 0x28 = 0b00 → high=0; low=0x77=119 → 0*256+119 = 119
        assertThat(dist.value()).isEqualTo(119.0);
    }

    // ================================================================
    //  A1 — Allarmi auto-segnalati dal device (byte 4 dell'header)
    // ================================================================

    @Test
    @DisplayName("A1 — byte 4 = 0x00: nessun flag → nessun Alarm e contact_reason esposto come Measure")
    void deviceAlarms_noFlagsNoAlarms() {
        byte[] payload = buildMeasurePayload(new byte[]{ 0x0A, 0x5B, 0x28, 0x77 });
        payload[3] = 0x01;   // contact reason = Scheduled
        payload[4] = 0x00;   // alarm status = nessun flag

        DecodedPacket pkt = decode(payload);

        assertThat(pkt.alarms()).isEmpty();
        assertThat(findMeasure(pkt, "contact_reason_flags").value()).isEqualTo(1.0);
        assertThat(findMeasure(pkt, "alarm_status_flags").value()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("A1 — byte 4 bit 0 (Limit 1) → genera Alarm DEVICE_LIMIT_1")
    void deviceAlarms_limit1() {
        byte[] payload = buildMeasurePayload(new byte[]{ 0x0A, 0x5B, 0x28, 0x77 });
        payload[4] = 0x01;

        DecodedPacket pkt = decode(payload);

        assertThat(pkt.alarms()).extracting(Alarm::alarmCode)
                .containsExactly(AlarmCodes.ALARM_DEVICE_LIMIT_1);
    }

    @Test
    @DisplayName("A1 — byte 4 bit 1 (Limit 2) → genera Alarm DEVICE_LIMIT_2")
    void deviceAlarms_limit2() {
        byte[] payload = buildMeasurePayload(new byte[]{ 0x0A, 0x5B, 0x28, 0x77 });
        payload[4] = 0x02;

        DecodedPacket pkt = decode(payload);

        assertThat(pkt.alarms()).extracting(Alarm::alarmCode)
                .containsExactly(AlarmCodes.ALARM_DEVICE_LIMIT_2);
    }

    @Test
    @DisplayName("A1 — byte 4 bit 2 (Limit 3) → genera Alarm DEVICE_LIMIT_3")
    void deviceAlarms_limit3() {
        byte[] payload = buildMeasurePayload(new byte[]{ 0x0A, 0x5B, 0x28, 0x77 });
        payload[4] = 0x04;

        DecodedPacket pkt = decode(payload);

        assertThat(pkt.alarms()).extracting(Alarm::alarmCode)
                .containsExactly(AlarmCodes.ALARM_DEVICE_LIMIT_3);
    }

    @Test
    @DisplayName("A1 — byte 4 bit 3 (Bund Status) → genera Alarm DEVICE_BUND_STATUS")
    void deviceAlarms_bund() {
        byte[] payload = buildMeasurePayload(new byte[]{ 0x0A, 0x5B, 0x28, 0x77 });
        payload[4] = 0x08;

        DecodedPacket pkt = decode(payload);

        assertThat(pkt.alarms()).extracting(Alarm::alarmCode)
                .containsExactly(AlarmCodes.ALARM_DEVICE_BUND_STATUS);
    }

    @Test
    @DisplayName("A1 — byte 4 = 0x89 (Active + Limit1 + Bund): bit 7 ignorato, generati 2 Alarm")
    void deviceAlarms_multipleFlagsActiveBitIgnored() {
        byte[] payload = buildMeasurePayload(new byte[]{ 0x0A, 0x5B, 0x28, 0x77 });
        payload[4] = (byte) 0x89;   // 1000 1001 = Active + Bund + Limit1

        DecodedPacket pkt = decode(payload);

        // L'Active bit (bit 7) è informativo, non un allarme.
        assertThat(pkt.alarms()).extracting(Alarm::alarmCode)
                .containsExactlyInAnyOrder(
                        AlarmCodes.ALARM_DEVICE_LIMIT_1,
                        AlarmCodes.ALARM_DEVICE_BUND_STATUS);
        assertThat(findMeasure(pkt, "alarm_status_flags").value()).isEqualTo(137.0); // 0x89
    }

    // ================================================================
    //  computeDeclaredBodyLength — helper statico
    // ================================================================

    @Test
    @DisplayName("computeDeclaredBodyLength — byte 15=0x04, byte 16=0x7B → 123 (caso tipico TEK822)")
    void declaredBodyLength_typicalMsg4() {
        byte[] data = new byte[17];
        data[15] = 0x04;
        data[16] = 0x7B;
        assertThat(Tek822Decoder.computeDeclaredBodyLength(data)).isEqualTo(123);
    }

    @Test
    @DisplayName("computeDeclaredBodyLength — bits[7:6] di byte 15 contribuiscono × 256 (NON bits[5:4])")
    void declaredBodyLength_highBitsContribute() {
        byte[] data = new byte[17];
        // 0xC4 = 0b11000100 → bits[7:6] = 0b11 = 3 → contributo = 3 × 256 = 768
        // bits[5:0] = 000100 = 4 (msgType valido)
        data[15] = (byte) 0xC4;
        data[16] = 0x10; // 16
        assertThat(Tek822Decoder.computeDeclaredBodyLength(data)).isEqualTo(768 + 16);
    }

    @Test
    @DisplayName("computeDeclaredBodyLength — Msg Type 16 (byte 15=0x10, byte 16=0x46) → 70, NON 326")
    void declaredBodyLength_msgType16_noShiftConflict() {
        byte[] data = new byte[17];
        // 0x10 = 0b00010000 → bits[7:6] = 0 (contributo 0), bits[5:0] = msgType 16
        data[15] = 0x10;
        data[16] = 0x46; // 70 byte body
        // Regression: il vecchio codice usava >> 4 e dava (1 × 256) + 70 = 326 ERRATO
        assertThat(Tek822Decoder.computeDeclaredBodyLength(data)).isEqualTo(70);
    }

    @Test
    @DisplayName("computeDeclaredBodyLength — byte 16 trattato come unsigned (0xFF = 255, non -1)")
    void declaredBodyLength_byte16Unsigned() {
        byte[] data = new byte[17];
        data[15] = 0x04;
        data[16] = (byte) 0xFF;
        assertThat(Tek822Decoder.computeDeclaredBodyLength(data)).isEqualTo(255);
    }

    // ================================================================
    //  Test di integrazione su payload reali (sample XLSM/PDF)
    // ================================================================

    /**
     * Payload campione Msg #4 preso dallo sheet "822" del file
     * configuration_and_command_v1.21.xlsm (riga R0007, colonna "Message Examples").
     *
     * Decodifica byte-per-byte:
     *   00      = 0x18  product type → TEK822 V2 NB        (NON estratto come Measure)
     *   01      = 0x02  HW revision                         (NON estratto)
     *   02      = 0x03  FW revision                         (NON estratto)
     *   03      = 0x41  contact reason (Scheduled+Manual)   (NON estratto - vedi A1)
     *   04      = 0x89  alarm status (Active+Limit1+Bund)   (NON estratto - vedi A1)
     *   05      = 0x19  CSQ = 25                            (NON estratto)
     *   06      = 0x36  battery: LTE=0, RTC=1, batt=70.9%   (NON estratto)
     *   07..14  = 08 64 43 10 47 98 70 54  → IMEI "0864431047987054"
     *   15      = 0x04  Message Type = 4
     *   16      = 0x7B  payload length = 123
     *   17..18  = 0x09 0x32  message count = 2354           (NON estratto)
     *   19      = 0x48  bits[7:5]=tryTickets=2, bits[4:0]=RTC hh=8
     *   20..21  = 0x00 0x0B  energy used = 11 mAh           (NON estratto)
     *   22      = 0xFF  bit7=1 → NB-IoT                     → network.tech_code=2
     *   23      = 0x81  not 0x00/0x80 → (0x81&0x7F)×15 = 15 min logger speed
     *   24      = 0x03  login time = 3×5 = 15 s             → network.login_time_s=15
     *   25      = 0x00  RTC mm = 0 → base timestamp 08:00
     *   26..29  = 0A 68 2B FE  misura 0: aux2=10, temp=0x68/2-30=22°C, aux1=10, dist=(3<<8)|0xFE=1022
     *   30..33  = 0A 68 2B FE  misura 1: identica alla 0
     *   34..37  = 0A 6A 2B FE  misura 2: temp=0x6A/2-30=23°C, dist=1022
     *   38..41  = 0A 6A 2B FE  misura 3: temp=23°C, dist=1022
     *   42..45  = 0A 6A 2B FE  misura 4: temp=23°C, dist=1022
     *   46..49  = 0A 6A 28 00  misura 5: temp=23°C, dist=(0<<8)|0=0
     *   50..53  = 0A 6A 2B FE  misura 6: temp=23°C, dist=1022
     *   54..57  = 0A 6A 28 43  misura 7: temp=23°C, dist=(0<<8)|0x43=67
     *   58..61  = 0A 6A 28 1E  misura 8: temp=23°C, dist=30
     *   62..65  = 0A 6A 28 62  misura 9: temp=23°C, dist=98
     *   66..69  = 0A 6A 2B 70  misura 10: temp=23°C, dist=(3<<8)|0x70=880
     *   70..73  = 0A 6A 2B FE  misura 11: temp=23°C, dist=1022
     *   74..137 = tutto a 0    16 slot vuoti (skipped)
     *   138..139= 2F 1D        CRC (non validato)
     */
    private static final String XLSM_MSG4_SAMPLE =
            "180203418919360864431047987054047B093248000BFF8103" +
            "00" +
            "0A682BFE0A682BFE0A6A2BFE0A6A2BFE0A6A2BFE0A6A28000A6A2BFE" +
            "0A6A28430A6A281E0A6A28620A6A2B700A6A2BFE" +
            "000000000000000000000000000000000000000000000000" +
            "000000000000000000000000000000000000000000000000" +
            "00000000000000000000" +
            "2F1D";

    @Test
    @DisplayName("Integration — payload reale XLSM Msg#4: 12 misure non-zero + diagnostica di rete")
    void realPayload_xlsmMsg4_decodesAllMeasures() {
        byte[] payload = hexToBytes(XLSM_MSG4_SAMPLE);
        // Verifica header: Msg #4 con byte 16 = 0x7B (123 = lunghezza body annunciata)
        assertThat(payload[15]).isEqualTo((byte) 0x04);
        assertThat(payload[16] & 0xFF).isEqualTo(0x7B);

        DecodedPacket pkt = decode(payload);

        // ---- Diagnostica di rete ----
        Measure techCode = findMeasure(pkt, "network.tech_code");
        assertThat(techCode.value()).as("byte 22 bit7=1 → NB-IoT (codice 2)").isEqualTo(2.0);

        Measure loginTime = findMeasure(pkt, "network.login_time_s");
        assertThat(loginTime.value()).as("byte 24=0x03 → 3×5=15s").isEqualTo(15.0);

        // ---- Misure: ne attendiamo 12 valide ----
        List<Measure> distances = pkt.measures().stream()
                .filter(m -> "distance_cm".equals(m.obisCode()))
                .toList();
        List<Measure> temperatures = pkt.measures().stream()
                .filter(m -> "temperature_c".equals(m.obisCode()))
                .toList();

        assertThat(distances).as("12 slot non-zero su 28 totali").hasSize(12);
        assertThat(temperatures).hasSize(12);

        // ---- Distanze: sequenza nota dal sample (in ordine di slot) ----
        // [1022, 1022, 1022, 1022, 1022, 0, 1022, 67, 30, 98, 880, 1022]
        assertThat(distances).extracting(Measure::value)
                .containsExactly(1022.0, 1022.0, 1022.0, 1022.0, 1022.0,
                                 0.0,    1022.0, 67.0,   30.0,   98.0,
                                 880.0,  1022.0);

        // ---- Temperature: prime 2 a 22°C, le restanti 10 a 23°C ----
        assertThat(temperatures).extracting(Measure::value)
                .containsExactly(22.0, 22.0,
                                 23.0, 23.0, 23.0, 23.0, 23.0, 23.0,
                                 23.0, 23.0, 23.0, 23.0);

        // ---- Aux1/Aux2 sanity check: tutti = 10 ----
        assertThat(pkt.measures().stream().filter(m -> "aux1".equals(m.obisCode())))
                .allMatch(m -> m.value() == 10.0);
        assertThat(pkt.measures().stream().filter(m -> "aux2".equals(m.obisCode())))
                .allMatch(m -> m.value() == 10.0);

        // ---- Diagnostica header (byte 0, 1, 2, 5, 6) — match con XLSM ----
        // Byte 0 = 0x18: code=24, label=TEK822 V2 NB
        Measure prodType = findMeasure(pkt, "header.product_type");
        assertThat(prodType.value()).isEqualTo(24.0);
        assertThat(prodType.unit()).isEqualTo("TEK822 V2 NB");
        // Byte 1 = 0x02: minor=2, major=0 → 2.0 + BG96
        Measure hwRev = findMeasure(pkt, "header.hw_revision");
        assertThat(hwRev.value()).isEqualTo(2.0);
        assertThat(hwRev.unit()).isEqualTo("BG96");
        // Byte 2 = 0x03: fwMajor=3, fwMinor=0 → 3.0 + "FW 3.0"
        Measure fwRev = findMeasure(pkt, "header.fw_revision");
        assertThat(fwRev.value()).isEqualTo(3.0);
        assertThat(fwRev.unit()).isEqualTo("FW 3.0");
        // Byte 5 = 0x19: GSM Rssi (rinominato da csq) = 25
        assertThat(findMeasure(pkt, "header.gsm_rssi").value()).isEqualTo(25.0);
        // 0x36 → 22*100/31 = 70.9677... → XLSM mostra "70,97% Capacity Remaining"
        Measure batt = findMeasure(pkt, "header.battery_percent");
        assertThat(batt.value()).isEqualTo(70.97);
        assertThat(batt.unit()).isEqualTo("% Capacity Remaining");
        assertThat(findMeasure(pkt, "header.rtc_set").value()).isEqualTo(1.0);       // bit 5 = 1
        assertThat(findMeasure(pkt, "header.lte_active").value()).isEqualTo(0.0);    // bit 6 = 0

        // ---- Network tech & MNC (byte 22 = 0xFF) ----
        // Bit 7 = 1 → NB; lower 4 bits = 0x0F = 15 (MNC)
        Measure tech = findMeasure(pkt, "network.tech_code");
        assertThat(tech.value()).isEqualTo(2.0);
        assertThat(tech.unit()).isEqualTo("NB");
        assertThat(findMeasure(pkt, "network.mnc").value()).isEqualTo(15.0);

        // ---- Diagnostica Msg #4 (byte 17-21) ----
        assertThat(findMeasure(pkt, "header.message_count").value()).isEqualTo(2354.0);    // 0x0932
        assertThat(findMeasure(pkt, "header.try_tickets_remaining").value()).isEqualTo(2.0); // 0x48 >> 5
        assertThat(findMeasure(pkt, "header.energy_used_mah").value()).isEqualTo(11.0);    // 0x000B

        // ---- Allarmi auto-segnalati (A1): byte 4 = 0x89 → Limit1 + Bund ----
        assertThat(pkt.alarms()).extracting(Alarm::alarmCode)
                .containsExactlyInAnyOrder(
                        AlarmCodes.ALARM_DEVICE_LIMIT_1,
                        AlarmCodes.ALARM_DEVICE_BUND_STATUS);
        // ---- Contact reason esposto come Measure: byte 3 = 0x41 ----
        assertThat(findMeasure(pkt, "contact_reason_flags").value()).isEqualTo(65.0); // 0x41

        // ---- Timestamp base = 08:00; gli slot successivi sono distanziati di 15 min ----
        // (logger speed = byte23=0x81 → (0x81 & 0x7F)*15 = 15 minuti)
        // I 4 measure di un singolo slot condividono lo stesso timestamp.
        Instant t0 = distances.get(0).timestamp();
        Instant t1 = distances.get(1).timestamp();
        assertThat(java.time.Duration.between(t1, t0).toMinutes())
                .as("ogni slot è 15 min più vecchio del precedente").isEqualTo(15);
    }

    /**
     * Payload campione Msg #6 (settings) costruito dall'esempio del PDF §2.2.3
     * più il CRC trailer 0xF83B (sample XLSM "822" R0010, abbreviato per i settings
     * che ci interessano testare in modo numerico).
     *
     * Body ASCII: "S0=80,S2=7F2000,S22=181A,S26=80"
     *   S0=80     → 0x80 = 128
     *   S2=7F2000 → 0x7F2000 = 8331264
     *   S22=181A  → 0x181A = 6170
     *   S26=80    → 0x80 = 128
     */
    @Test
    @DisplayName("Integration — Msg#6 multi-setting hex: tutti i registri numerici sono decodificati")
    void realPayload_msg6_decodesAllHexSettings() {
        String ascii = "S0=80,S2=7F2000,S22=181A,S26=80";
        byte[] payload = buildSettingsPayload(ascii);

        DecodedPacket pkt = decode(payload);

        assertThat(findMeasure(pkt, "setting.S0").value()).isEqualTo(128.0);
        assertThat(findMeasure(pkt, "setting.S2").value()).isEqualTo(8331264.0);
        assertThat(findMeasure(pkt, "setting.S22").value()).isEqualTo(6170.0);
        assertThat(findMeasure(pkt, "setting.S26").value()).isEqualTo(128.0);
    }

    /**
     * Payload Msg #16 (statistics) dal foglio XLSM "822". Verifica:
     *  - declaredBodyLength corretto (bug pre-fix: tornava 326 invece di 70)
     *  - tutti e 12 i campi stats emessi come Measure (prima ne mancavano 5)
     *  - ICCID preservato come stringa (in unit, value=0)
     */
    @Test
    @DisplayName("Integration — payload XLSM Msg#16 reale: tutti i 12 campi stats + body length 70")
    void realPayload_msg16_xlsmSheet_allStatsPresent() {
        String hex = "18020344891936086443104798705410462C"
                + "38393838323830363636303031303637353334382C" // ICCID
                + "3435323237332C302C38302C323335352C31382C"
                + "35383937342C3732362C362C33393439362C313639362C36302C"
                + "7EE0";
        byte[] payload = hexToBytes(hex);

        // 1. Body length corretto (bug pre-fix dava 326)
        assertThat(Tek822Decoder.computeDeclaredBodyLength(payload)).isEqualTo(70);

        // 2. Decodifica integrale
        DecodedPacket pkt = decode(payload);

        // ICCID preservato come stringa
        Measure iccid = findMeasure(pkt, "stats.iccid");
        assertThat(iccid.value()).isEqualTo(0.0);
        assertThat(iccid.unit()).isEqualTo("89882806660010675348");

        // Tutti i 12 campi attesi
        assertThat(findMeasure(pkt, "stats.energy_used_ma_minutes").value()).isEqualTo(452273.0);
        assertThat(findMeasure(pkt, "stats.min_temperature_c").value()).isEqualTo(0.0);
        assertThat(findMeasure(pkt, "stats.max_temperature_c").value()).isEqualTo(80.0);
        assertThat(findMeasure(pkt, "stats.message_count").value()).isEqualTo(2355.0);
        assertThat(findMeasure(pkt, "stats.delivery_fail").value()).isEqualTo(18.0);
        assertThat(findMeasure(pkt, "stats.total_send_time_s").value()).isEqualTo(58974.0);
        assertThat(findMeasure(pkt, "stats.max_send_time_s").value()).isEqualTo(726.0);
        assertThat(findMeasure(pkt, "stats.min_send_time_s").value()).isEqualTo(6.0);
        assertThat(findMeasure(pkt, "stats.rssi_total").value()).isEqualTo(39496.0);
        assertThat(findMeasure(pkt, "stats.rssi_valid_count").value()).isEqualTo(1696.0);
        assertThat(findMeasure(pkt, "stats.rssi_fail_count").value()).isEqualTo(60.0);
    }

    /**
     * Payload Msg #17 (GPS) dal foglio XLSM "822". Verifica:
     *  - header diagnostics (Fix A) ora popolato anche per Msg #17
     *  - tutti e 12 i campi GPS emessi come Measure (prima ne mancavano 8)
     *  - LAT/LON/UTC/Date preservati come stringhe NMEA in unit
     */
    @Test
    @DisplayName("Integration — payload XLSM Msg#17 reale: 12 campi GPS + header diagnostics popolato")
    void realPayload_msg17_xlsmSheet_allGpsFieldsAndHeader() {
        String hex = "18038204881160086443104798705411492C"
                + "39352C"                                 // [0] time_to_fix = 95
                + "3133343434322E302C"                     // [1] UTC = 134442.0
                + "353235352E393935304E2C"                 // [2] LAT = 5255.9950N
                + "30303833322E34343137572C"               // [3] LON = 00832.4417W
                + "312E392C"                               // [4] hdop = 1.9
                + "3132372E382C"                           // [5] altitude = 127.8
                + "322C"                                   // [6] fix_mode = 2
                + "302E30302C"                             // [7] heading = 0.00
                + "302E302C"                               // [8] speed_kmh = 0.0
                + "302E302C"                               // [9] speed_knots = 0.0
                + "3032313031352C"                         // [10] date = 021015
                + "30342C"                                 // [11] nSat = 04
                + "8843";                                  // CRC
        byte[] payload = hexToBytes(hex);

        // 1. Body length corretto (byte 16 = 0x49 = 73)
        assertThat(Tek822Decoder.computeDeclaredBodyLength(payload)).isEqualTo(73);

        DecodedPacket pkt = decode(payload);

        // 2. Header diagnostics (Fix A): ora popolato anche per Msg #17.
        // Byte 0 = 0x18 → TEK822 V2 NB
        Measure prodType = findMeasure(pkt, "header.product_type");
        assertThat(prodType.value()).isEqualTo(24.0);
        assertThat(prodType.unit()).isEqualTo("TEK822 V2 NB");
        // Byte 1 = 0x03 → minor=3, major=0 → "3.0" + BG96
        Measure hwRev = findMeasure(pkt, "header.hw_revision");
        assertThat(hwRev.value()).isEqualTo(3.0);
        assertThat(hwRev.unit()).isEqualTo("BG96");
        // Byte 2 = 0x82 → fwMajor=2, fwMinor=4 → "2.4" + FW 2.4
        Measure fwRev = findMeasure(pkt, "header.fw_revision");
        assertThat(fwRev.value()).isEqualTo(2.4);
        assertThat(fwRev.unit()).isEqualTo("FW 2.4");
        // Byte 5 = 0x11 → CSQ = 17
        assertThat(findMeasure(pkt, "header.gsm_rssi").value()).isEqualTo(17.0);
        // Byte 6 = 0x60 → battery 0%, RTC set, LTE Act
        assertThat(findMeasure(pkt, "header.battery_percent").value()).isEqualTo(0.0);

        // 3. GPS fields (Fix B): tutti i 12 campi presenti
        assertThat(findMeasure(pkt, "gps.time_to_fix_s").value()).isEqualTo(95.0);
        assertThat(findMeasure(pkt, "gps.utc").unit()).isEqualTo("134442.0");
        assertThat(findMeasure(pkt, "gps.latitude").unit()).isEqualTo("5255.9950N");
        assertThat(findMeasure(pkt, "gps.longitude").unit()).isEqualTo("00832.4417W");
        assertThat(findMeasure(pkt, "gps.hdop").value()).isEqualTo(1.9);
        assertThat(findMeasure(pkt, "gps.altitude_m").value()).isEqualTo(127.8);
        assertThat(findMeasure(pkt, "gps.fix_mode").value()).isEqualTo(2.0);
        assertThat(findMeasure(pkt, "gps.heading_deg").value()).isEqualTo(0.0);
        assertThat(findMeasure(pkt, "gps.speed_kmh").value()).isEqualTo(0.0);
        assertThat(findMeasure(pkt, "gps.speed_knots").value()).isEqualTo(0.0);
        assertThat(findMeasure(pkt, "gps.date").unit()).isEqualTo("021015");
        assertThat(findMeasure(pkt, "gps.satellites").value()).isEqualTo(4.0);
    }

    /**
     * Payload Msg #6 dal foglio XLSM "822" cella H1, riga A187 mostra l'ASCII
     * decodificato. Verifica che il decoder emetta TUTTI i 30 setting (S0..S29)
     * — inclusi APN, IP e i setting vuoti — non solo quelli hex-parseable.
     */
    @Test
    @DisplayName("Integration — payload XLSM Msg#6 reale: 30 setting emessi (no perdita APN/IP)")
    void realPayload_msg6_xlsmSheet_preservesAllSettings() {
        String ascii = "S0=81,S1=00,S2=7F2000,S3=82,S4=FFE8,S5=7C14,S6=0000,"
                + "S7=00,S8=00,S9=,S10=,S11=,"
                + "S12=iot.1nce.net,S13=,S14=,"
                + "S15=173.212.215.131,S16=9002,S17=7200,"
                + "S18=C8,S19=06,S20=00,S21=,S22=,"
                + "S23=11,S24=00,S25=00,S26=80,S27=88,S28=,S29=00";
        byte[] payload = buildSettingsPayload(ascii);

        DecodedPacket pkt = decode(payload);

        List<Measure> settings = pkt.measures().stream()
                .filter(m -> m.obisCode().startsWith("setting."))
                .toList();
        // 30 setting da S0 a S29
        assertThat(settings).hasSize(30);

        // Spot check su quelli che il vecchio codice perdeva:
        assertThat(findMeasure(pkt, "setting.S12").unit()).isEqualTo("iot.1nce.net");
        assertThat(findMeasure(pkt, "setting.S15").unit()).isEqualTo("173.212.215.131");
        assertThat(findMeasure(pkt, "setting.S9").unit()).isEqualTo("");
        assertThat(findMeasure(pkt, "setting.S28").unit()).isEqualTo("");

        // Spot check su quelli hex-parseable (devono restare corretti):
        assertThat(findMeasure(pkt, "setting.S0").value()).isEqualTo(129.0);       // 0x81
        assertThat(findMeasure(pkt, "setting.S2").value()).isEqualTo(8331264.0);   // 0x7F2000
        assertThat(findMeasure(pkt, "setting.S4").value()).isEqualTo(65512.0);     // 0xFFE8
        assertThat(findMeasure(pkt, "setting.S16").value()).isEqualTo(36866.0);    // 0x9002
        assertThat(findMeasure(pkt, "setting.S26").value()).isEqualTo(128.0);      // 0x80
    }

    // ================================================================
    //  Helpers
    // ================================================================

    /**
     * Converte una stringa esadecimale (es. "180203...") in array di byte.
     * Ignora gli spazi così è facile incollare payload "letti a colpo d'occhio".
     */
    private static byte[] hexToBytes(String hex) {
        String clean = hex.replaceAll("\\s+", "");
        if (clean.length() % 2 != 0) {
            throw new IllegalArgumentException("Lunghezza hex dispari: " + clean.length());
        }
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }


    private DecodedPacket decode(byte[] payload) {
        decoder.doDecode(ctx, DEVICE_ID, payload);
        ArgumentCaptor<DecodedPacket> captor = ArgumentCaptor.forClass(DecodedPacket.class);
        verify(ctx).publishDecodedData(eq(DEVICE_ID), captor.capture());
        return captor.getValue();
    }

    private Measure findMeasure(DecodedPacket pkt, String obisCode) {
        Optional<Measure> m = pkt.measures().stream()
                .filter(x -> obisCode.equals(x.obisCode()))
                .findFirst();
        assertThat(m).as("misura '%s' presente nel packet", obisCode).isPresent();
        return m.get();
    }

    /**
     * Costruisce un payload Msg #6 (settings) con header minimale di 17 byte +
     * stringa ASCII dei settings + 2 byte di CRC fittizio.
     */
    private byte[] buildSettingsPayload(String ascii) {
        byte[] body = ascii.getBytes(StandardCharsets.US_ASCII);
        byte[] payload = new byte[17 + body.length + 2];
        payload[15] = 0x06;                       // msg type 6
        payload[16] = (byte) body.length;         // length lo
        System.arraycopy(body, 0, payload, 17, body.length);
        // CRC trailer fittizio (non validato dal decoder)
        payload[payload.length - 2] = (byte) 0xDE;
        payload[payload.length - 1] = (byte) 0xAD;
        return payload;
    }

    /**
     * Costruisce un payload Msg #4 (measures) con N×4 byte di misure a partire
     * dall'offset 26 + 2 byte di CRC fittizio. Imposta logger speed = 0x80 (15 min).
     */
    private byte[] buildMeasurePayload(byte[]... measures) {
        int payloadLen = 26 + measures.length * 4 + 2;
        byte[] payload = new byte[payloadLen];
        payload[15] = 0x04;                       // msg type 4
        payload[16] = (byte) (payloadLen - 17);   // length lo (informativo, non usato)
        payload[19] = 0x10;                       // RTC hours = 16 (bits[4:0])
        payload[23] = (byte) 0x80;                // logger speed = 15 min
        payload[25] = 0x00;                       // RTC minutes = 0
        for (int i = 0; i < measures.length; i++) {
            System.arraycopy(measures[i], 0, payload, 26 + i * 4, 4);
        }
        payload[payload.length - 2] = (byte) 0xDE;
        payload[payload.length - 1] = (byte) 0xAD;
        return payload;
    }
}
