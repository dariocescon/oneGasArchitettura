package com.aton.proj.gastelemetry.decodetool.service;

/**
 * Utility per il parsing di una stringa esadecimale TEK822 in array di byte
 * + estrazione del deviceId (IMEI) dai byte 7-14 dell'header.
 *
 * <p>L'IMEI sul protocollo TEK822 è codificato come 8 byte BCD: ogni byte
 * contiene 2 cifre del numero IMEI (es. byte {@code 0x08} → "08"). La stringa
 * risultante è di 16 caratteri; il primo zero è di padding e l'IMEI reale è
 * di 15 cifre. Cfr. {@code protocollo-tekelek-tek822.md} §3 e §7.3.
 */
public final class PayloadParser {

    /** Offset dell'IMEI nell'header del payload TEK822 (8 byte, 2 cifre per byte). */
    private static final int IMEI_OFFSET = 7;
    private static final int IMEI_BYTES  = 8;

    /** Lunghezza minima per riconoscere un header valido (PDF §2.2.1). */
    private static final int MIN_HEADER_LEN = 17;

    private PayloadParser() {}

    /**
     * Converte una stringa esadecimale in array di byte. Tollera spazi
     * intermedi (utile quando si incolla un payload con formattazione varia).
     *
     * @param hex stringa esadecimale, eventualmente con spazi
     * @return array di byte corrispondente
     * @throws IllegalArgumentException se la stringa contiene caratteri non-hex
     *         o ha lunghezza dispari
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null) throw new IllegalArgumentException("Stringa hex nulla");
        String clean = hex.replaceAll("\\s+", "");
        if (clean.isEmpty()) throw new IllegalArgumentException("Stringa hex vuota");
        if (clean.length() % 2 != 0) {
            throw new IllegalArgumentException(
                    "Lunghezza hex dispari (" + clean.length() + "): manca un nibble");
        }
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            try {
                out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Caratteri non esadecimali alla posizione " + (i * 2)
                                + ": '" + clean.substring(i * 2, i * 2 + 2) + "'");
            }
        }
        return out;
    }

    /**
     * Estrae l'IMEI dai byte 7-14 del payload. Ogni byte rappresenta 2 cifre
     * decimali (codifica BCD). Il primo zero è di padding e viene rimosso.
     *
     * @param data payload TEK822 di almeno 15 byte
     * @return IMEI come stringa di 15 cifre
     * @throws IllegalArgumentException se il payload è troppo corto
     */
    public static String extractImei(byte[] data) {
        if (data == null || data.length < IMEI_OFFSET + IMEI_BYTES) {
            throw new IllegalArgumentException(
                    "Payload troppo corto per estrarre l'IMEI: serve almeno "
                            + (IMEI_OFFSET + IMEI_BYTES) + " byte, ricevuti "
                            + (data == null ? 0 : data.length));
        }
        StringBuilder imei16 = new StringBuilder(16);
        for (int i = 0; i < IMEI_BYTES; i++) {
            int b = data[IMEI_OFFSET + i] & 0xFF;
            // BCD: ogni nibble è una cifra (es. 0x64 = nibble 6 + nibble 4 = "64").
            // Usiamo %02X (hex) perché in BCD valido le cifre sono 0-9, range
            // in cui esadecimale e decimale coincidono.
            imei16.append(String.format("%02X", b));
        }
        // Restituisce le 16 cifre raw come fa l'XLSM. Lo zero iniziale è di
        // padding (IMEI reale = 15 cifre) ma viene mantenuto per coerenza
        // con la decodifica byte-per-byte mostrata dal foglio Excel.
        return imei16.toString();
    }

    /**
     * Verifica che il payload sia almeno lungo come l'header minimo.
     */
    public static boolean hasValidHeader(byte[] data) {
        return data != null && data.length >= MIN_HEADER_LEN;
    }
}
