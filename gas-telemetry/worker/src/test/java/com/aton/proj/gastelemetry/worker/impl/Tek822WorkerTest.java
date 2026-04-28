package com.aton.proj.gastelemetry.worker.impl;

import com.aton.proj.gastelemetry.common.CommandsPacket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test della <b>composizione finale</b> dei comandi in {@link Tek822Worker#composeAsciiPayload}.
 *
 * Regola portata da {@code onGas_Meteor_claude.ControllerUtils.concatenateCommands}:
 *   - primo comando: password inclusa
 *   - successivi: tutto fino alla prima virgola (inclusa) viene rimosso
 *   - join con virgola, output US-ASCII
 *
 * Il <i>fix</i> del passo 5 della roadmap rimuove il bug in cui il worker inviava
 * stringhe HEX (rappresentazione testuale) invece dei byte ASCII reali.
 */
class Tek822WorkerTest {

    @Test
    @DisplayName("composeAsciiPayload concatena un singolo comando senza modifiche")
    void compose_singleCommand() {
        CommandsPacket packet = new CommandsPacket(1, new String[]{"TEK822,R6=02"});

        byte[] payload = Tek822Worker.composeAsciiPayload(packet);

        assertThat(new String(payload, StandardCharsets.US_ASCII)).isEqualTo("TEK822,R6=02");
    }

    @Test
    @DisplayName("composeAsciiPayload rimuove la password duplicata dai comandi successivi")
    void compose_dedupsPassword() {
        CommandsPacket packet = new CommandsPacket(1, new String[]{
                "TEK822,S0=80",
                "TEK822,S1=01",
                "TEK822,R3=ACTIVE"
        });

        byte[] payload = Tek822Worker.composeAsciiPayload(packet);

        assertThat(new String(payload, StandardCharsets.US_ASCII))
                .isEqualTo("TEK822,S0=80,S1=01,R3=ACTIVE");
    }

    @Test
    @DisplayName("composeAsciiPayload preserva password custom (no hardcoding di 'TEK822')")
    void compose_customPassword() {
        CommandsPacket packet = new CommandsPacket(1, new String[]{
                "MYPASS,S0=80",
                "MYPASS,R3=ACTIVE"
        });

        byte[] payload = Tek822Worker.composeAsciiPayload(packet);

        assertThat(new String(payload, StandardCharsets.US_ASCII))
                .isEqualTo("MYPASS,S0=80,R3=ACTIVE");
    }

    @Test
    @DisplayName("composeAsciiPayload gestisce SET_APN (multi-registro nello stesso comando)")
    void compose_multiRegisterCommand() {
        // SET_APN produce nativamente "TEK822,S12=apn,S13=user,S14=pass" — la
        // password va rimossa solo se questo comando NON è il primo.
        CommandsPacket packet = new CommandsPacket(1, new String[]{
                "TEK822,S12=stream.co.uk,S13=streamip,S14=streamip",
                "TEK822,R3=ACTIVE"
        });

        byte[] payload = Tek822Worker.composeAsciiPayload(packet);

        assertThat(new String(payload, StandardCharsets.US_ASCII))
                .isEqualTo("TEK822,S12=stream.co.uk,S13=streamip,S14=streamip,R3=ACTIVE");
    }

    @Test
    @DisplayName("composeAsciiPayload con array vuoto restituisce byte[0]")
    void compose_empty() {
        CommandsPacket packet = new CommandsPacket(0, new String[0]);
        assertThat(Tek822Worker.composeAsciiPayload(packet)).isEmpty();
    }

    @Test
    @DisplayName("composeAsciiPayload produce byte ASCII reali (non rappresentazione HEX)")
    void compose_emitsAsciiBytesNotHexChars() {
        // Regression test: il bug pre-fix emetteva i caratteri "5","4","4","5",...
        // (cioè la rappresentazione HEX di "TE...") al posto dei byte 'T','E',...
        CommandsPacket packet = new CommandsPacket(1, new String[]{"TEK822,R6=02"});

        byte[] payload = Tek822Worker.composeAsciiPayload(packet);

        // Primo byte deve essere 'T' (0x54), non '5' (0x35)
        assertThat(payload[0]).isEqualTo((byte) 'T');
        assertThat(payload[0]).isEqualTo((byte) 0x54);
    }
}
