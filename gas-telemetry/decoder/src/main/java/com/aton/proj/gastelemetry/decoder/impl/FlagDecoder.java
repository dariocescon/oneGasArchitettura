package com.aton.proj.gastelemetry.decoder.impl;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper di decodifica delle bitmask presenti nell'header TEK822 — byte 3
 * (Contact Reason, PDF §2.2.1.2) e byte 4 (Alarm/Status, PDF §2.2.1.3) —
 * in liste di label umani.
 *
 * <p>Centralizza la mappa bit → nome così che esista <b>un'unica fonte
 * autoritativa</b> di questa conoscenza di protocollo, accessibile a tutti
 * i consumer del modulo decoder (worker, command-api, decode-tool, futuri).
 *
 * <p>Non è invocato internamente da {@link Tek822Decoder}: gli alarm del
 * byte 4 sono già prodotti come {@code Alarm} con codici dedicati
 * ({@code DEVICE_LIMIT_1}, ...). Questo helper serve a chi vuole esporre
 * <b>entrambi</b> i livelli — il byte raw e la sua decomposizione in flag
 * leggibili — tipicamente per visualizzazione o auditing.
 */
public final class FlagDecoder {

    // ---- Bit mask Contact Reason (byte 3) — PDF §2.2.1.2 ----
    public static final int CR_SCHEDULED      = 0x01;
    public static final int CR_ALARM          = 0x02;
    public static final int CR_SERVER_REQUEST = 0x04;
    public static final int CR_MANUAL         = 0x08;
    public static final int CR_REBOOT         = 0x10;
    public static final int CR_TSP_REQUESTED  = 0x20;
    public static final int CR_DYN_LIM        = 0x40;
    public static final int CR_DYN_LIM_2      = 0x80;

    // ---- Bit mask Alarm/Status (byte 4) — PDF §2.2.1.3 ----
    public static final int AS_LIMIT_1 = 0x01;
    public static final int AS_LIMIT_2 = 0x02;
    public static final int AS_LIMIT_3 = 0x04;
    public static final int AS_BUND    = 0x08;
    public static final int AS_ACTIVE  = 0x80;

    private FlagDecoder() {}

    /**
     * Restituisce le label dei bit attivi nel byte di Contact Reason.
     * Esempio: {@code 0x41} (= {@code 0b01000001}) → {@code ["Scheduled", "DynLim"]}.
     *
     * @param b byte (verrà mascherato a 0..255)
     * @return lista — vuota se nessun bit è settato; mantiene l'ordine dei bit dal LSB al MSB
     */
    public static List<String> contactReasonFlags(int b) {
        int v = b & 0xFF;
        List<String> out = new ArrayList<>();
        if ((v & CR_SCHEDULED)      != 0) out.add("Scheduled");
        if ((v & CR_ALARM)          != 0) out.add("Alarm");
        if ((v & CR_SERVER_REQUEST) != 0) out.add("ServerRequest");
        if ((v & CR_MANUAL)         != 0) out.add("Manual");
        if ((v & CR_REBOOT)         != 0) out.add("Reboot");
        if ((v & CR_TSP_REQUESTED)  != 0) out.add("TSP");
        if ((v & CR_DYN_LIM)        != 0) out.add("DynLim");
        if ((v & CR_DYN_LIM_2)      != 0) out.add("DynLim2");
        return out;
    }

    /**
     * Restituisce le label dei bit attivi nel byte di Alarm/Status.
     * Esempio: {@code 0x89} (= {@code 0b10001001}) → {@code ["Limit1", "Bund", "Active"]}.
     *
     * <p>Il bit {@code Active} (bit 7) è informativo, non rappresenta un allarme.
     *
     * @param b byte (verrà mascherato a 0..255)
     * @return lista — vuota se nessun bit è settato
     */
    public static List<String> alarmStatusFlags(int b) {
        int v = b & 0xFF;
        List<String> out = new ArrayList<>();
        if ((v & AS_LIMIT_1) != 0) out.add("Limit1");
        if ((v & AS_LIMIT_2) != 0) out.add("Limit2");
        if ((v & AS_LIMIT_3) != 0) out.add("Limit3");
        if ((v & AS_BUND)    != 0) out.add("Bund");
        if ((v & AS_ACTIVE)  != 0) out.add("Active");
        return out;
    }
}
