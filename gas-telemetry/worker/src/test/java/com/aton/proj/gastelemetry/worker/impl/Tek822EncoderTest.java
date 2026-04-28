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
}
