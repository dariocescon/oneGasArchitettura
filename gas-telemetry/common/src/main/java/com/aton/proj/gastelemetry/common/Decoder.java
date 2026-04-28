package com.aton.proj.gastelemetry.common;

public interface Decoder {

    /**
     * Decodifica un pacchetto grezzo in misure e allarmi.
     * Il contratto è:
     *   1. Parsare il payload binario → misure con timestamp originale del device
     *   2. Calcolare allarmi derivati dalla configurazione (soglie, trasmissioni mancanti, ecc.)
     *   3. Pubblicare il DecodedPacket via ctx.publishDecodedData()
     *
     * Il timestamp deve essere quello contenuto nel pacchetto (RTC del device),
     * non il timestamp di ricezione del server.
     */
    void doDecode(DecoderContext ctx, String deviceId, byte[] data);
}
