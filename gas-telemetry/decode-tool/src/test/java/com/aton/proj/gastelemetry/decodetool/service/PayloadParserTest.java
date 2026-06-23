package com.aton.proj.gastelemetry.decodetool.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayloadParserTest {

    @Test
    @DisplayName("hexToBytes converte stringa hex valida ignorando spazi")
    void hexToBytes_basic() {
        byte[] out = PayloadParser.hexToBytes("18 02 03 41");
        assertThat(out).containsExactly((byte) 0x18, (byte) 0x02, (byte) 0x03, (byte) 0x41);
    }

    @Test
    @DisplayName("hexToBytes rifiuta lunghezza dispari")
    void hexToBytes_rejectOddLength() {
        assertThatThrownBy(() -> PayloadParser.hexToBytes("1802F"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dispari");
    }

    @Test
    @DisplayName("hexToBytes rifiuta caratteri non-hex")
    void hexToBytes_rejectNonHex() {
        assertThatThrownBy(() -> PayloadParser.hexToBytes("18ZZ"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non esadecimali");
    }

    @Test
    @DisplayName("hexToBytes rifiuta stringa nulla o vuota")
    void hexToBytes_rejectEmpty() {
        assertThatThrownBy(() -> PayloadParser.hexToBytes(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PayloadParser.hexToBytes("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("extractImei dal payload XLSM (byte 7-14 = 08 64 43 10 47 98 70 54)")
    void extractImei_xlsmSample() {
        byte[] header = new byte[15];
        header[7]  = (byte) 0x08;
        header[8]  = (byte) 0x64;
        header[9]  = (byte) 0x43;
        header[10] = (byte) 0x10;
        header[11] = (byte) 0x47;
        header[12] = (byte) 0x98;
        header[13] = (byte) 0x70;
        header[14] = (byte) 0x54;

        // Concatenazione cifre BCD: 08 64 43 10 47 98 70 54 → "0864431047987054"
        // (16 cifre raw, come visualizzato dall'XLSM; lo zero iniziale è padding
        // per arrivare a 16 caratteri ma viene mantenuto per coerenza col foglio).
        assertThat(PayloadParser.extractImei(header)).isEqualTo("0864431047987054");
    }

    @Test
    @DisplayName("extractImei rifiuta payload troppo corto")
    void extractImei_rejectShort() {
        byte[] tooShort = new byte[10];
        assertThatThrownBy(() -> PayloadParser.extractImei(tooShort))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("troppo corto");
    }

    @Test
    @DisplayName("hasValidHeader: < 17 byte → false; >= 17 → true")
    void hasValidHeader_thresholds() {
        assertThat(PayloadParser.hasValidHeader(new byte[16])).isFalse();
        assertThat(PayloadParser.hasValidHeader(new byte[17])).isTrue();
        assertThat(PayloadParser.hasValidHeader(null)).isFalse();
    }
}
