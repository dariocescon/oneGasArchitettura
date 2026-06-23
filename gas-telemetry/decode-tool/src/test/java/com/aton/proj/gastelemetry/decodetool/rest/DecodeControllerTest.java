package com.aton.proj.gastelemetry.decodetool.rest;

import com.aton.proj.gastelemetry.decoder.impl.Tek822Decoder;
import com.aton.proj.gastelemetry.decodetool.config.DecoderBeansConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test end-to-end del decode-tool via {@link MockMvc} (richiesta in-process,
 * no socket TCP — compatibile con ambienti sandbox che bloccano il loopback).
 */
@WebMvcTest(DecodeController.class)
@Import({ DecoderBeansConfig.class })
class DecodeControllerTest {

    /**
     * Payload Msg #4 dallo sheet "822" dell'XLSM (riga R0007). 12 misure non-zero,
     * byte 4 = 0x89 → Active + Bund + Limit 1 → due Alarm attesi.
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

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    /**
     * Sanity check sulla DI: verifica che il bean Tek822Decoder sia caricato
     * (cioè che le esclusioni Maven non abbiano rotto il classpath).
     */
    @Autowired private Tek822Decoder decoder;

    @Test
    @DisplayName("POST /api/decode con payload XLSM → 200 + IMEI 16 cifre + body length 123 + 2 allarmi device")
    void decode_xlsmMsg4_returnsImeiAndAlarms() throws Exception {
        assertThat(decoder).isNotNull();

        String body = mapper.writeValueAsString(new DecodeRequest(XLSM_MSG4_HEX));

        String responseJson = mvc.perform(post("/api/decode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                // IMEI BCD raw a 16 cifre (come mostrato dall'XLSM)
                .andExpect(jsonPath("$.deviceId").value("0864431047987054"))
                // Source hex IMEI: byte 7-14 del payload
                .andExpect(jsonPath("$.deviceIdSourceHex").value("0864431047987054"))
                .andExpect(jsonPath("$.deviceIdByteRange").value("7-14"))
                .andExpect(jsonPath("$.messageType").value(4))
                .andExpect(jsonPath("$.messageTypeSourceHex").value("04"))
                .andExpect(jsonPath("$.messageTypeByteRange").value("15"))
                // byte 16 = 0x7B = 123: lunghezza body dichiarata dal device
                .andExpect(jsonPath("$.declaredBodyLength").value(123))
                // Body length: solo byte 16 (in XLSM coincide). Bits[5:4] di byte 15
                // contribuirebbero × 256 ma sono 0 nei TEK822 reali.
                .andExpect(jsonPath("$.declaredBodyLengthSourceHex").value("7B"))
                .andExpect(jsonPath("$.declaredBodyLengthByteRange").value("16"))
                .andExpect(jsonPath("$.contactReasonHex").value("41"))
                .andExpect(jsonPath("$.contactReasonByteRange").value("3"))
                // 0x41 = bit 0 (Scheduled) + bit 6 (DynLim): decodificato server-side da FlagDecoder
                .andExpect(jsonPath("$.contactReasonFlags").isArray())
                .andExpect(jsonPath("$.contactReasonFlags[0]").value("Scheduled"))
                .andExpect(jsonPath("$.contactReasonFlags[1]").value("DynLim"))
                .andExpect(jsonPath("$.alarmStatusHex").value("89"))
                .andExpect(jsonPath("$.alarmStatusByteRange").value("4"))
                // 0x89 = Limit1 + Bund + Active
                .andExpect(jsonPath("$.alarmStatusFlags[0]").value("Limit1"))
                .andExpect(jsonPath("$.alarmStatusFlags[1]").value("Bund"))
                .andExpect(jsonPath("$.alarmStatusFlags[2]").value("Active"))
                .andReturn().getResponse().getContentAsString();

        // Verifica programmatica sul JSON (più robusta di jsonPath per array)
        JsonNode root = mapper.readTree(responseJson);

        // ---- Allarmi: byte 4 sorgente per entrambi ----
        JsonNode alarms = root.get("alarms");
        assertThat(alarms).hasSize(2);
        assertThat(alarms.findValues("alarmCode"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("DEVICE_LIMIT_1", "DEVICE_BUND_STATUS");
        for (JsonNode a : alarms) {
            assertThat(a.get("sourceHex").asText())
                    .as("source hex degli allarmi device = byte 4")
                    .isEqualTo("89");
            assertThat(a.get("byteRange").asText())
                    .as("byte range degli allarmi device = '4'")
                    .isEqualTo("4");
        }

        // ---- Misure: source hex coerente con la specifica ----
        JsonNode measures = root.get("measures");
        assertThat(measures.size()).isGreaterThan(40);

        // header.product_type → byte 0 = 0x18
        JsonNode prodType = findMeasure(measures, "header.product_type");
        assertThat(prodType.get("sourceHex").asText()).isEqualTo("18");
        assertThat(prodType.get("byteRange").asText()).isEqualTo("0");
        // header.hw_revision → byte 1 = 0x02
        JsonNode hwRev = findMeasure(measures, "header.hw_revision");
        assertThat(hwRev.get("sourceHex").asText()).isEqualTo("02");
        assertThat(hwRev.get("byteRange").asText()).isEqualTo("1");
        // header.message_count → byte 17-18 = 0x0932
        JsonNode msgCount = findMeasure(measures, "header.message_count");
        assertThat(msgCount.get("sourceHex").asText()).isEqualTo("0932");
        assertThat(msgCount.get("byteRange").asText()).isEqualTo("17-18");
        // header.energy_used_mah → byte 20-21 = 0x000B
        JsonNode energy = findMeasure(measures, "header.energy_used_mah");
        assertThat(energy.get("sourceHex").asText()).isEqualTo("000B");
        assertThat(energy.get("byteRange").asText()).isEqualTo("20-21");

        // Prima misura time-series (slot 0 a offset 26) per ognuno dei 4 sub-byte
        // distance_cm → byte j+2 + j+3 = byte 28-29
        JsonNode firstDistance = firstMeasureByObis(measures, "distance_cm");
        assertThat(firstDistance.get("sourceHex").asText()).isEqualTo("2BFE");
        assertThat(firstDistance.get("byteRange").asText()).isEqualTo("28-29");
        // temperature_c → byte j+1 = byte 27
        JsonNode firstTemp = firstMeasureByObis(measures, "temperature_c");
        assertThat(firstTemp.get("sourceHex").asText()).isEqualTo("68");
        assertThat(firstTemp.get("byteRange").asText()).isEqualTo("27");
        // aux2 → byte j = byte 26
        JsonNode firstAux2 = firstMeasureByObis(measures, "aux2");
        assertThat(firstAux2.get("sourceHex").asText()).isEqualTo("0A");
        assertThat(firstAux2.get("byteRange").asText()).isEqualTo("26");
    }

    private static JsonNode findMeasure(JsonNode measures, String obisCode) {
        for (JsonNode m : measures) {
            if (obisCode.equals(m.get("obisCode").asText())) return m;
        }
        throw new AssertionError("misura non trovata: " + obisCode);
    }

    private static JsonNode firstMeasureByObis(JsonNode measures, String obisCode) {
        return findMeasure(measures, obisCode);
    }

    @Test
    @DisplayName("POST /api/decode con hex malformata → 400 + 'non esadecimali'")
    void decode_malformedHex_returns400() throws Exception {
        String body = mapper.writeValueAsString(new DecodeRequest("18ZZ02"));

        mvc.perform(post("/api/decode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("non esadecimali")));
    }

    @Test
    @DisplayName("POST /api/decode con payload < 17 byte → 400 + 'troppo corto'")
    void decode_tooShort_returns400() throws Exception {
        String body = mapper.writeValueAsString(new DecodeRequest("1802034189"));

        mvc.perform(post("/api/decode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("troppo corto")));
    }

    @Test
    @DisplayName("POST /api/decode con campo hex vuoto → 400")
    void decode_emptyHex_returns400() throws Exception {
        String body = mapper.writeValueAsString(new DecodeRequest(""));

        mvc.perform(post("/api/decode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
