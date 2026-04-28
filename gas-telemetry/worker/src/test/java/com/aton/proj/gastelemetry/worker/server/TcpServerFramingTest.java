package com.aton.proj.gastelemetry.worker.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test del framing TEK822 in {@link TcpServer#readFramedMessage(InputStream)} e
 * {@link TcpServer#readExactly(InputStream, int)}.
 *
 * Logica testata (portata da onGas_Meteor_claude):
 *  - leggi 17 byte di header
 *  - estrai declaredLength da byte 15 (bit 7-6 = high) + byte 16 (low)
 *  - leggi esattamente declaredLength byte di body
 *  - concatena
 *
 * I sample usano payload reali tratti dal manuale TEK822 v1.21 (sezione 2.2).
 */
class TcpServerFramingTest {

    /**
     * Sample Msg #4 con dimensioni esatte: 17 byte header + 123 byte body = 140 byte.
     * Byte 15 = 0x04 → msgType=4, length hi=0; byte 16 = 0x7B → length lo=123 → declaredLength=123.
     *
     * Composizione body (123 byte):
     *   - 9 byte: message count + RTC/diag (byte 17-25)
     *   - 112 byte: 28 slot da 4 byte (di cui 25 con misure non-zero, 3 vuoti)
     *   - 2 byte: CRC trailer
     */
    private static final byte[] SAMPLE_MSG4 = HexFormat.of().parseHex(
            "08018104861475086107502100455104" +                                               // bytes 0-15 (header start)
            "7B" +                                                                             // byte 16 (length lo)
            "00019700000082010F" +                                                             // bytes 17-25 (msgCount, RTC, ...)
            "0A5B28770A5B28770A5B28760A5B28770A5B28770A5B28770A5B28770A5B28760A5B28760A5B2876" + // 10 measures
            "0A5B28770A5B28760A5B28760A5B28760A5D28770A5D28770A5D28770A5D28770A5D28770A5D2877" + // 10 measures
            "0A5D28770A5D28770A5D28770A5D28770A5F2877" +                                       // 5 measures
            "000000000000000000000000" +                                                       // 3 measure-slot vuoti
            "EEBA");                                                                            // CRC

    // ================================================================
    //  readExactly
    // ================================================================

    @Test
    @DisplayName("readExactly legge tutti i byte richiesti in una sola read")
    void readExactly_singleChunk() throws IOException {
        byte[] expected = {1, 2, 3, 4, 5};
        byte[] result = TcpServer.readExactly(new ByteArrayInputStream(expected), 5);
        assertThat(result).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("readExactly riassembla letture frammentate (caso comune NB-IoT)")
    void readExactly_fragmentedReads() throws IOException {
        // Simulo TCP che consegna i dati in 3 segmenti: [1,2], [3], [4,5]
        InputStream fragmented = new SequenceInputStream(
                Collections.enumeration(Arrays.asList(
                        new ByteArrayInputStream(new byte[]{1, 2}),
                        new ByteArrayInputStream(new byte[]{3}),
                        new ByteArrayInputStream(new byte[]{4, 5}))));

        byte[] result = TcpServer.readExactly(fragmented, 5);
        assertThat(result).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("readExactly lancia IOException se lo stream finisce prima del previsto")
    void readExactly_premature_eof() {
        byte[] partial = {1, 2, 3};
        assertThatThrownBy(() -> TcpServer.readExactly(new ByteArrayInputStream(partial), 5))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Stream chiuso dopo 3 di 5 byte attesi");
    }

    // ================================================================
    //  readFramedMessage
    // ================================================================

    @Test
    @DisplayName("readFramedMessage decodifica header + body con declaredLength=123 (sample manuale)")
    void readFramedMessage_realPayload() throws IOException {
        byte[] result = TcpServer.readFramedMessage(new ByteArrayInputStream(SAMPLE_MSG4));

        assertThat(result).hasSize(140);
        assertThat(result).containsExactly(SAMPLE_MSG4);
        // Sanity check: byte 15 e 16 hanno gli stessi valori del payload originale
        assertThat(result[15] & 0xFF).isEqualTo(0x04);
        assertThat(result[16] & 0xFF).isEqualTo(0x7B);
    }

    @Test
    @DisplayName("readFramedMessage gestisce declaredLength=0 (solo header)")
    void readFramedMessage_headerOnly() throws IOException {
        byte[] headerOnly = new byte[17];
        Arrays.fill(headerOnly, (byte) 0xAB);
        headerOnly[15] = 0x00;  // msgType=0, length hi=0
        headerOnly[16] = 0x00;  // length lo=0

        byte[] result = TcpServer.readFramedMessage(new ByteArrayInputStream(headerOnly));
        assertThat(result).hasSize(17);
        assertThat(result).containsExactly(headerOnly);
    }

    @Test
    @DisplayName("readFramedMessage usa correttamente i 2 high-bit di byte 15 per length>=256")
    void readFramedMessage_largeBodyUses10BitLength() throws IOException {
        // Costruisco un payload con declaredLength = 256 (= 1 << 8)
        // → byte 15 high bits[7:6] = 0b01, low bits = msgType (0b000100 = 4)
        // → byte 15 = 0b01000100 = 0x44
        // → byte 16 = 0x00
        byte[] header = new byte[17];
        header[15] = 0x44;
        header[16] = 0x00;
        byte[] body = new byte[256];
        Arrays.fill(body, (byte) 0xCD);

        byte[] frame = new byte[17 + 256];
        System.arraycopy(header, 0, frame, 0, 17);
        System.arraycopy(body, 0, frame, 17, 256);

        byte[] result = TcpServer.readFramedMessage(new ByteArrayInputStream(frame));

        assertThat(result).hasSize(17 + 256);
        // Verifica che il body sia stato letto interamente
        for (int i = 17; i < result.length; i++) {
            assertThat(result[i] & 0xFF).isEqualTo(0xCD);
        }
    }

    @Test
    @DisplayName("readFramedMessage propaga IOException se lo stream chiude durante il body")
    void readFramedMessage_truncatedBody() {
        // Header dichiara body=10, ma lo stream contiene solo 5 byte di body
        byte[] truncated = new byte[17 + 5];
        truncated[15] = 0x00;  // length hi=0
        truncated[16] = 0x0A;  // length lo=10 → declaredLength=10
        // body solo 5 byte (truncated)

        assertThatThrownBy(() -> TcpServer.readFramedMessage(new ByteArrayInputStream(truncated)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("di 10 byte attesi");
    }

    @Test
    @DisplayName("readFramedMessage propaga IOException se lo stream chiude prima dell'header")
    void readFramedMessage_truncatedHeader() {
        byte[] partialHeader = new byte[10]; // meno di 17

        assertThatThrownBy(() -> TcpServer.readFramedMessage(new ByteArrayInputStream(partialHeader)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("di 17 byte attesi");
    }
}
