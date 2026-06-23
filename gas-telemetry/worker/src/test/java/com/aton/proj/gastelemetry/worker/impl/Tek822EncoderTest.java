package com.aton.proj.gastelemetry.worker.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test dell'output ASCII di {@link Tek822Encoder}.
 *
 * <p>Sample attesi tratti dal manuale TEK822 §3.20-3.21 e dai test del progetto
 * di riferimento {@code onGas_Meteor_claude.Tek822EncoderTest}.
 *
 * <p>Dopo il fix del passo 5 della roadmap l'encoder restituisce direttamente
 * stringhe ASCII (es. {@code "TEK822,S0=90"}), non più la rappresentazione HEX.
 */
class Tek822EncoderTest {

    private final Tek822Encoder encoder = new Tek822Encoder();
    private static final String DEVICE_ID = "111111111111111";
    private static final String DEVICE_TYPE = "TEK822V1";

    private static CommandEntry cmd(String type, Map<String, Object> params) {
        CommandEntry e = new CommandEntry(DEVICE_ID, DEVICE_TYPE, type);
        e.parameters().putAll(params);
        return e;
    }

    @Test
    @DisplayName("SET_INTERVAL: interval=4h, samplingPeriod=1 → S0=128+16=144=0x90")
    void setInterval() {
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_INTERVAL, Map.of("interval", 4, "samplingPeriod", 1))
        ));

        // S-command → REBOOT auto-appended
        assertThat(out).hasSize(2);
        assertThat(out.get(0)).isEqualTo("TEK822,S0=90");
        assertThat(out.get(1)).isEqualTo("TEK822,R3=ACTIVE");
    }

    @Test
    @DisplayName("SET_LISTEN: 30 minutes → S1=06")
    void setListen() {
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_LISTEN, Map.of("listenMinutes", 30))
        ));
        assertThat(out.get(0)).isEqualTo("TEK822,S1=06");
    }

    @Test
    @DisplayName("REQUEST_STATUS è R-command: nessun REBOOT auto-appended")
    void requestStatus_noReboot() {
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_REQUEST_STATUS, Map.of())
        ));
        assertThat(out).containsExactly("TEK822,R6=02");
    }

    @Test
    @DisplayName("REBOOT esplicito non viene duplicato")
    void explicitReboot_noDuplication() {
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_INTERVAL, Map.of("interval", 4, "samplingPeriod", 1)),
                cmd(Tek822Encoder.CMD_REBOOT, Map.of())
        ));
        assertThat(out).hasSize(2)
                .last().isEqualTo("TEK822,R3=ACTIVE");
    }

    @Test
    @DisplayName("Custom password viene propagata e usata anche nel REBOOT auto-appended")
    void customPasswordPropagatedToReboot() {
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_INTERVAL,
                        Map.of("interval", 4, "samplingPeriod", 1, "password", "MYPASS"))
        ));
        assertThat(out.get(0)).isEqualTo("MYPASS,S0=90");
        assertThat(out.get(1)).isEqualTo("MYPASS,R3=ACTIVE");
    }

    @Test
    @DisplayName("SET_APN produce 3 registri nello stesso comando")
    void setApn() {
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_APN,
                        Map.of("apn", "stream.co.uk", "username", "streamip", "apnPassword", "streamip"))
        ));
        assertThat(out.get(0))
                .isEqualTo("TEK822,S12=stream.co.uk,S13=streamip,S14=streamip");
        assertThat(out.get(1)).isEqualTo("TEK822,R3=ACTIVE");
    }

    @Test
    @DisplayName("Comando sconosciuto solleva IllegalArgumentException")
    void unknownCommand() {
        assertThatThrownBy(() -> encoder.encode(List.of(cmd("UNKNOWN", Map.of()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");
    }

    // ================================================================
    //  Nuovi S-register: S3, S7/S8, S11, S17, S18, S19, S20,
    //                    S21, S22, S23, S24, S26, S29
    // ================================================================

    @Test
    @DisplayName("SET_CONTROL_CONFIG: networkMode=3 + verboseTsp → S3=83 (esempio XLSM)")
    void setControlConfig_xlsmExample() {
        // Match dell'esempio XLSM 822 CC R0032-R0039
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_CONTROL_CONFIG,
                        Map.of("networkMode", 3, "verboseTsp", true))
        ));
        assertThat(out.get(0)).isEqualTo("TEK822,S3=83");
        assertThat(out.get(1)).isEqualTo("TEK822,R3=ACTIVE");
    }

    @Test
    @DisplayName("SET_CONTROL_CONFIG: solo network mode CAT-M1 (0) → S3=00")
    void setControlConfig_catM1Default() {
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_CONTROL_CONFIG, Map.of("networkMode", 0))
        ));
        assertThat(out.get(0)).isEqualTo("TEK822,S3=00");
    }

    @Test
    @DisplayName("SET_DYNAMIC_LIMIT_1: polarity=true, enabled=true, rate=27 → S7=DB")
    void setDynamicLimit1() {
        // S7 = (1 << 7) | (1 << 6) | 27 = 128 + 64 + 27 = 219 = 0xDB
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_DYNAMIC_LIMIT_1,
                        Map.of("polarity", true, "enabled", true, "rate", 27))
        ));
        assertThat(out.get(0)).isEqualTo("TEK822,S7=DB");
    }

    @Test
    @DisplayName("SET_DYNAMIC_LIMIT_2: default disabilitato → S8=00")
    void setDynamicLimit2_default() {
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_DYNAMIC_LIMIT_2, Map.of())
        ));
        assertThat(out.get(0)).isEqualTo("TEK822,S8=00");
    }

    @Test
    @DisplayName("SET_PASSWORD: nuova password ASCII")
    void setPassword() {
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_PASSWORD, Map.of("newPassword", "NEW123"))
        ));
        assertThat(out.get(0)).isEqualTo("TEK822,S11=NEW123");
    }

    @Test
    @DisplayName("SET_BATTERY_CAPACITY: 7200 mAh → S17=1C20 (esempio PDF §3.10)")
    void setBatteryCapacity() {
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_BATTERY_CAPACITY, Map.of("capacityMah", 7200))
        ));
        assertThat(out.get(0)).isEqualTo("TEK822,S17=1C20");
    }

    @Test
    @DisplayName("SET_F_STOP: 5V → S18=C8 (esempio PDF §3.20.8)")
    void setFStop() {
        // (200 × 5) / 5 = 200 = 0xC8
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_F_STOP, Map.of("voltage", 5.0))
        ));
        assertThat(out.get(0)).isEqualTo("TEK822,S18=C8");
    }

    @Test
    @DisplayName("SET_CONTROL3_CONFIG: APN auth None → S19=01 (PDF §3.20.9)")
    void setControl3Config() {
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_CONTROL3_CONFIG, Map.of("apnAuthNone", true))
        ));
        assertThat(out.get(0)).isEqualTo("TEK822,S19=01");
    }

    @Test
    @DisplayName("SET_E_STOP: 1V → S20=28 (esempio PDF §3.20.10)")
    void setEStop() {
        // (200 × 1) / 5 = 40 = 0x28
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_E_STOP, Map.of("voltage", 1.0))
        ));
        assertThat(out.get(0)).isEqualTo("TEK822,S20=28");
    }

    @Test
    @DisplayName("SET_MCC_MNC: ASCII operator short code (Vodafone IE)")
    void setMccMnc() {
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_MCC_MNC, Map.of("mccMnc", "27201"))
        ));
        assertThat(out.get(0)).isEqualTo("TEK822,S21=27201");
    }

    @Test
    @DisplayName("SET_LTE_BAND: B12 → S22=800 (lookup PDF §3.20.12)")
    void setLteBand_band12() {
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_LTE_BAND, Map.of("bandCode", 0x800L))
        ));
        assertThat(out.get(0)).isEqualTo("TEK822,S22=800");
    }

    @Test
    @DisplayName("SET_LTE_BAND: B39 = 4 miliardi → richiede long (overflow Integer)")
    void setLteBand_band39_long() {
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_LTE_BAND, Map.of("bandCode", 4_000_000_000L))
        ));
        // 4_000_000_000 = 0xEE6B2800
        assertThat(out.get(0)).isEqualTo("TEK822,S22=EE6B2800");
    }

    @Test
    @DisplayName("SET_RETRY_CONFIG: 4 tickets, 30s → S23=13 (esempio PDF §3.20.13)")
    void setRetryConfig() {
        // X = 4-1 = 3; Y = (30/10)-1 = 2; S23 = 3 + (2 × 8) = 19 = 0x13
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_RETRY_CONFIG, Map.of("tryTickets", 4, "periodSec", 30))
        ));
        assertThat(out.get(0)).isEqualTo("TEK822,S23=13");
    }

    @Test
    @DisplayName("SET_SCHEDULE_DELAY: 5 minuti → S24=05 (esempio PDF §3.20.14)")
    void setScheduleDelay() {
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_SCHEDULE_DELAY, Map.of("delayMinutes", 5))
        ));
        assertThat(out.get(0)).isEqualTo("TEK822,S24=05");
    }

    @Test
    @DisplayName("SET_CONTROL2_CONFIG: adcRaw=true → S26=80 (esempio PDF §3.20.15)")
    void setControl2Config_adcRaw() {
        // bit 7 = 1 → S26 = 0x80
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_CONTROL2_CONFIG, Map.of("adcRaw", true))
        ));
        assertThat(out.get(0)).isEqualTo("TEK822,S26=80");
    }

    @Test
    @DisplayName("SET_CONTROL2_CONFIG: adcHighRes (bit 6) → S26=40")
    void setControl2Config_adcHighRes() {
        // bit 6 = 1 → S26 = 0x40 (percentuale 0-1000)
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_CONTROL2_CONFIG, Map.of("adcHighRes", true))
        ));
        assertThat(out.get(0)).isEqualTo("TEK822,S26=40");
    }

    @Test
    @DisplayName("SET_CONTROL4_CONFIG: disableLongRetry + offsetTimer=3 → S29=38 (esempio PDF §3.20.16)")
    void setControl4Config_xlsmExample() {
        // bit 3 = 1 (disableLongRetry) → 8
        // bits 4-5 = 3 (offset timer = 60min) → 3 << 4 = 48
        // Total = 8 + 48 = 56 = 0x38
        List<String> out = encoder.encode(List.of(
                cmd(Tek822Encoder.CMD_SET_CONTROL4_CONFIG,
                        Map.of("disableLongRetry", true, "dynLim1OffsetTimer", 3))
        ));
        assertThat(out.get(0)).isEqualTo("TEK822,S29=38");
    }

    @Test
    @DisplayName("Tutti i nuovi S-command triggerano REBOOT auto-append")
    void allNewSCommands_triggerReboot() {
        // Spot check su un campione misto
        List<String> commandTypes = List.of(
                Tek822Encoder.CMD_SET_CONTROL_CONFIG,
                Tek822Encoder.CMD_SET_DYNAMIC_LIMIT_1,
                Tek822Encoder.CMD_SET_BATTERY_CAPACITY,
                Tek822Encoder.CMD_SET_CONTROL2_CONFIG
        );
        for (String type : commandTypes) {
            List<String> out = encoder.encode(List.of(cmd(type, Map.of(
                    "networkMode", 0, "rate", 0, "capacityMah", 0,
                    "polarity", false, "enabled", false))));
            assertThat(out)
                    .as("Comando %s deve generare REBOOT auto-appended", type)
                    .hasSize(2);
            assertThat(out.get(1)).isEqualTo("TEK822,R3=ACTIVE");
        }
    }
}
