package com.aton.proj.gastelemetry.decodetool.rest;

import com.aton.proj.gastelemetry.common.DecodedPacket;
import com.aton.proj.gastelemetry.decoder.impl.FlagDecoder;
import com.aton.proj.gastelemetry.decoder.impl.Tek822Decoder;
import com.aton.proj.gastelemetry.decodetool.service.HexSlicer;
import com.aton.proj.gastelemetry.decodetool.service.InMemoryDecoderContext;
import com.aton.proj.gastelemetry.decodetool.service.PayloadParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller del decode-tool.
 *
 * <p>L'endpoint {@code POST /api/decode} riceve una stringa esadecimale di un
 * payload TEK822 e restituisce un {@link DecodeResponse} JSON con le misure e
 * gli allarmi decodificati, ognuno arricchito con i byte di payload sorgente
 * (vedi {@link HexSlicer}). Errori di input (hex malformata, payload troppo
 * corto) restituiscono HTTP 400 con body JSON {@code {error: "..."}}.
 */
@RestController
@RequestMapping("/api")
public class DecodeController {

    private static final Logger log = LoggerFactory.getLogger(DecodeController.class);

    private static final int MSG_TYPE_BYTE         = 15;
    private static final int CONTACT_REASON_OFFSET = 3;
    private static final int ALARM_STATUS_OFFSET   = 4;
    private static final int IMEI_OFFSET           = 7;
    private static final int IMEI_BYTES            = 8;

    private final Tek822Decoder decoder;

    public DecodeController(Tek822Decoder decoder) {
        this.decoder = decoder;
    }

    @PostMapping("/decode")
    public ResponseEntity<?> decode(@RequestBody DecodeRequest request) {
        if (request == null || request.hex() == null || request.hex().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Campo 'hex' mancante o vuoto"));
        }

        byte[] payload;
        try {
            payload = PayloadParser.hexToBytes(request.hex());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        if (!PayloadParser.hasValidHeader(payload)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Payload troppo corto: servono almeno 17 byte di header, "
                            + "ricevuti " + payload.length));
        }

        String deviceId;
        try {
            deviceId = PayloadParser.extractImei(payload);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        InMemoryDecoderContext ctx = new InMemoryDecoderContext();
        try {
            decoder.doDecode(ctx, deviceId, payload);
        } catch (RuntimeException e) {
            log.warn("Decodifica fallita per device {}: {}", deviceId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Errore durante la decodifica: " + e.getMessage()));
        }

        // Lunghezza body dichiarata dal device — formula PDF §2.2.1.
        // La logica è centralizzata in Tek822Decoder per non duplicarla qui.
        int declaredBodyLength = Tek822Decoder.computeDeclaredBodyLength(payload);

        DecodedPacket packet = ctx.getCaptured();
        List<MeasureView> measureViews = packet == null
                ? List.of()
                : HexSlicer.annotateMeasures(payload, packet.measures());
        List<AlarmView> alarmViews = packet == null
                ? List.of()
                : HexSlicer.annotateAlarms(payload, packet.alarms());

        DecodeResponse response = new DecodeResponse(
                deviceId,
                HexSlicer.sliceHex(payload, IMEI_OFFSET, IMEI_BYTES),
                HexSlicer.formatByteRange(IMEI_OFFSET, IMEI_BYTES),
                payload[MSG_TYPE_BYTE] & 0x3F,
                HexSlicer.sliceHex(payload, MSG_TYPE_BYTE, 1),
                HexSlicer.formatByteRange(MSG_TYPE_BYTE, 1),
                declaredBodyLength,
                // Body length è codificato principalmente nel byte 16; il byte 15
                // contribuisce solo via bits[5:4] (= 0 in pratica). L'XLSM mostra
                // anch'esso solo il byte 16 per "Payload_Len".
                HexSlicer.sliceHex(payload, MSG_TYPE_BYTE + 1, 1),
                HexSlicer.formatByteRange(MSG_TYPE_BYTE + 1, 1),
                payload.length,
                String.format("%02X", payload[CONTACT_REASON_OFFSET] & 0xFF),
                HexSlicer.formatByteRange(CONTACT_REASON_OFFSET, 1),
                FlagDecoder.contactReasonFlags(payload[CONTACT_REASON_OFFSET]),
                String.format("%02X", payload[ALARM_STATUS_OFFSET] & 0xFF),
                HexSlicer.formatByteRange(ALARM_STATUS_OFFSET, 1),
                FlagDecoder.alarmStatusFlags(payload[ALARM_STATUS_OFFSET]),
                measureViews,
                alarmViews
        );
        return ResponseEntity.ok(response);
    }
}
