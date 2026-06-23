package com.aton.proj.gastelemetry.decodetool.service;

import com.aton.proj.gastelemetry.common.DecodedPacket;
import com.aton.proj.gastelemetry.decoder.impl.Tek822Decoder;
import com.aton.proj.gastelemetry.decodetool.rest.AlarmView;
import com.aton.proj.gastelemetry.decodetool.rest.MeasureView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test di copertura del mapping tra {@code Tek822Decoder} e {@link HexSlicer}.
 *
 * <p><b>Scopo</b>: ogni {@code Measure}/{@code Alarm} prodotto dal decoder deve
 * avere un mapping in {@link HexSlicer} (entry in {@code FIXED_OFFSETS},
 * gestione in {@code slotMeasureSlice} o entry nel switch di
 * {@code annotateAlarms}). Se manca, {@code MeasureView#byteRange} o
 * {@code AlarmView#byteRange} risulta {@code null} e l'utente del decode-tool
 * vede una colonna "Byte" vuota — sintomo di una nuova measure/alarm aggiunta
 * al decoder senza aver esteso il mapping nel tool.
 *
 * <p>Questi test girano contro:
 * <ul>
 *   <li>Il payload XLSM Msg #4 di riferimento (12 slot non vuoti, byte 4 = 0x89
 *       → 2 alarm device su 4 codici possibili)</li>
 *   <li>Un payload sintetico con byte 4 = 0xFF, che forza l'emissione di tutti
 *       e 4 i codici alarm device (LIMIT_1/2/3, BUND)</li>
 * </ul>
 */
class HexSlicerCoverageTest {

    /**
     * Payload campione Msg #4 (stesso usato in DecodeControllerTest e
     * Tek822DecoderTest). Byte 4 = 0x89 → emette DEVICE_LIMIT_1 + DEVICE_BUND_STATUS.
     */
    private static final String XLSM_MSG4_HEX =
            "180203418919360864431047987054047B093248000BFF8103" +
            "00" +
            "0A682BFE0A682BFE0A6A2BFE0A6A2BFE0A6A2BFE0A6A28000A6A2BFE" +
            "0A6A28430A6A281E0A6A28620A6A2B700A6A2BFE" +
            "000000000000000000000000000000000000000000000000" +
            "000000000000000000000000000000000000000000000000" +
            "00000000000000000000" +
            "2F1D";

    /**
     * Variante con byte 4 = 0xFF per forzare l'emissione di TUTTI e 4 i codici
     * alarm device (LIMIT_1, LIMIT_2, LIMIT_3, BUND). Bit 7 (Active) è
     * informativo, non genera un alarm.
     */
    private static final String SYNTHETIC_ALL_ALARMS_HEX =
            // byte 4 = "FF" al posto di "89" (offset hex 8-9)
            XLSM_MSG4_HEX.substring(0, 8) + "FF" + XLSM_MSG4_HEX.substring(10);

    private final Tek822Decoder decoder = new Tek822Decoder();

    @Test
    @DisplayName("Copertura — ogni Measure prodotta dal decoder Msg #4 ha byteRange + sourceHex mappati")
    void msg4_allMeasuresHaveSourceMapping() {
        DecodedPacket pkt = runDecoder(XLSM_MSG4_HEX);
        byte[] payload = PayloadParser.hexToBytes(XLSM_MSG4_HEX);

        List<MeasureView> views = HexSlicer.annotateMeasures(payload, pkt.measures());

        List<String> unmapped = views.stream()
                .filter(v -> v.byteRange() == null || v.sourceHex() == null)
                .map(MeasureView::obisCode)
                .distinct()
                .sorted()
                .toList();

        assertThat(unmapped)
                .as("Le seguenti measure prodotte dal Tek822Decoder non hanno mapping in HexSlicer.\n"
                        + "Per ognuna, aggiungere una entry a HexSlicer.FIXED_OFFSETS (offset header)\n"
                        + "oppure estendere slotMeasureSlice() (per-slot misure). Codici scoperti:")
                .isEmpty();
    }

    @Test
    @DisplayName("Copertura — ogni Alarm prodotto con byte 4 = 0xFF ha byteRange + sourceHex mappati")
    void allFourDeviceAlarms_allAlarmCodesMapped() {
        DecodedPacket pkt = runDecoder(SYNTHETIC_ALL_ALARMS_HEX);
        byte[] payload = PayloadParser.hexToBytes(SYNTHETIC_ALL_ALARMS_HEX);

        // Sanity: il payload sintetico deve davvero produrre 4 alarm (uno per bit 0-3 di byte 4)
        assertThat(pkt.alarms()).hasSize(4);

        List<AlarmView> views = HexSlicer.annotateAlarms(payload, pkt.alarms());

        // Verifica che siano coperti tutti e 4 i codici noti
        assertThat(views).extracting(AlarmView::alarmCode)
                .containsExactlyInAnyOrder(
                        "DEVICE_LIMIT_1", "DEVICE_LIMIT_2",
                        "DEVICE_LIMIT_3", "DEVICE_BUND_STATUS");

        List<String> unmapped = views.stream()
                .filter(v -> v.byteRange() == null || v.sourceHex() == null)
                .map(AlarmView::alarmCode)
                .distinct()
                .sorted()
                .toList();

        assertThat(unmapped)
                .as("I seguenti alarm code prodotti dal Tek822Decoder non hanno mapping nel switch\n"
                        + "di HexSlicer.annotateAlarms. Aggiungerli al case match. Codici scoperti:")
                .isEmpty();
    }

    private DecodedPacket runDecoder(String hex) {
        byte[] payload = PayloadParser.hexToBytes(hex);
        InMemoryDecoderContext ctx = new InMemoryDecoderContext();
        decoder.doDecode(ctx, PayloadParser.extractImei(payload), payload);
        DecodedPacket pkt = ctx.getCaptured();
        assertThat(pkt).as("Il decoder ha catturato un DecodedPacket").isNotNull();
        return pkt;
    }
}
