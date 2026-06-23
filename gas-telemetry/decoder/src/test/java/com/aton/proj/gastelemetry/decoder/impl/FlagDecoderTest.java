package com.aton.proj.gastelemetry.decoder.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test di {@link FlagDecoder}: copertura puntuale dei singoli bit per Contact
 * Reason (byte 3, PDF §2.2.1.2) e Alarm/Status (byte 4, PDF §2.2.1.3).
 */
class FlagDecoderTest {

    // ============================================================
    //  Contact Reason — byte 3
    // ============================================================

    @Test
    @DisplayName("Contact Reason — byte 0x00 nessun flag → lista vuota")
    void contactReason_none() {
        assertThat(FlagDecoder.contactReasonFlags(0x00)).isEmpty();
    }

    @Test
    @DisplayName("Contact Reason — bit 0 → 'Scheduled'")
    void contactReason_scheduled() {
        assertThat(FlagDecoder.contactReasonFlags(0x01)).containsExactly("Scheduled");
    }

    @Test
    @DisplayName("Contact Reason — 0x41 = Scheduled + DynLim (payload XLSM Msg #4)")
    void contactReason_xlsmMsg4() {
        assertThat(FlagDecoder.contactReasonFlags(0x41))
                .containsExactly("Scheduled", "DynLim");
    }

    @Test
    @DisplayName("Contact Reason — 0x42 = Alarm + DynLim (payload XLSM Msg #8)")
    void contactReason_xlsmMsg8() {
        assertThat(FlagDecoder.contactReasonFlags(0x42))
                .containsExactly("Alarm", "DynLim");
    }

    @Test
    @DisplayName("Contact Reason — 0xFF tutti i bit → tutti gli 8 flag in ordine LSB→MSB")
    void contactReason_all() {
        assertThat(FlagDecoder.contactReasonFlags(0xFF))
                .containsExactly("Scheduled", "Alarm", "ServerRequest", "Manual",
                                 "Reboot", "TSP", "DynLim", "DynLim2");
    }

    // ============================================================
    //  Alarm/Status — byte 4
    // ============================================================

    @Test
    @DisplayName("Alarm Status — byte 0x00 nessun flag → lista vuota")
    void alarmStatus_none() {
        assertThat(FlagDecoder.alarmStatusFlags(0x00)).isEmpty();
    }

    @Test
    @DisplayName("Alarm Status — 0x89 = Limit1 + Bund + Active (payload XLSM Msg #4)")
    void alarmStatus_xlsmMsg4() {
        assertThat(FlagDecoder.alarmStatusFlags(0x89))
                .containsExactly("Limit1", "Bund", "Active");
    }

    @Test
    @DisplayName("Alarm Status — 0xFF tutti i bit settati → 4 alarm + Active (bit 4-6 riservati, ignorati)")
    void alarmStatus_all() {
        assertThat(FlagDecoder.alarmStatusFlags(0xFF))
                .containsExactly("Limit1", "Limit2", "Limit3", "Bund", "Active");
    }

    @Test
    @DisplayName("Alarm Status — solo Active (0x80) → ['Active']")
    void alarmStatus_activeOnly() {
        assertThat(FlagDecoder.alarmStatusFlags(0x80)).containsExactly("Active");
    }

    // ============================================================
    //  Robustezza
    // ============================================================

    @Test
    @DisplayName("Maschera 0xFF — int negativi vengono trattati come unsigned (-1 = 0xFF)")
    void handlesNegativeByteAsUnsigned() {
        // byte = (byte) 0xFF è -1 in Java signed. Il metodo deve trattarlo come 255.
        assertThat(FlagDecoder.contactReasonFlags(-1)).hasSize(8);
        assertThat(FlagDecoder.alarmStatusFlags(-1)).hasSize(5);
    }
}
