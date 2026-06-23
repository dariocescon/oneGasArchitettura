package com.aton.proj.gastelemetry.decodetool.rest;

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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test end-to-end del nuovo endpoint {@code POST /api/encode} via MockMvc.
 */
@WebMvcTest(EncodeController.class)
@Import({ DecoderBeansConfig.class })
class EncodeControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    @Test
    @DisplayName("POST /api/encode con SET_INTERVAL → ASCII S0=90 + REBOOT auto-appended + CRLF")
    void encode_setInterval() throws Exception {
        EncodeRequest req = new EncodeRequest(
                "0864431047987054", "TEK822V2", "TEK822",
                List.of(new EncodeRequest.CommandSpec("SET_INTERVAL",
                        Map.of("interval", 4, "samplingPeriod", 1)))
        );

        String responseJson = mvc.perform(post("/api/encode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.individualCommands.length()").value(2))
                .andExpect(jsonPath("$.individualCommands[0]").value("TEK822,S0=90"))
                .andExpect(jsonPath("$.individualCommands[1]").value("TEK822,R3=ACTIVE"))
                // composedAscii termina con \r\n (CRLF terminator per fix B3)
                .andExpect(jsonPath("$.composedAscii").value("TEK822,S0=90,R3=ACTIVE\r\n"))
                .andReturn().getResponse().getContentAsString();

        JsonNode root = mapper.readTree(responseJson);

        // Hex bytes corrispondenti
        // TEK822,S0=90,R3=ACTIVE\r\n
        // T=54 E=45 K=4B 8=38 2=32 2=32 ,=2C ... \r=0D \n=0A
        String hex = root.get("composedHex").asText();
        assertThat(hex).startsWith("54454B383232"); // "TEK822"
        assertThat(hex).endsWith("0D0A");           // CRLF

        // Persistence row preview
        JsonNode rows = root.get("persistenceRows");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("deviceId").asText()).isEqualTo("0864431047987054");
        assertThat(rows.get(0).get("commandType").asText()).isEqualTo("SET_INTERVAL");
        assertThat(rows.get(0).get("status").asText()).isEqualTo("PENDING");
        assertThat(rows.get(0).get("commandParams").asText())
                .contains("\"interval\":4")
                .contains("\"samplingPeriod\":1");
    }

    @Test
    @DisplayName("POST /api/encode con SET_APN + SET_SERVER → 3 individual + composedAscii dedup-ata")
    void encode_setApn_setServer() throws Exception {
        EncodeRequest req = new EncodeRequest(
                "DEV1", "TEK822V2", "TEK822",
                List.of(
                        new EncodeRequest.CommandSpec("SET_APN", Map.of(
                                "apn", "iot.1nce.net",
                                "username", "", "apnPassword", "")),
                        new EncodeRequest.CommandSpec("SET_SERVER", Map.of(
                                "serverIp", "173.212.215.131",
                                "serverPort", "9002"))
                )
        );

        mvc.perform(post("/api/encode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                // 2 comandi utente + REBOOT auto-appended = 3
                .andExpect(jsonPath("$.individualCommands.length()").value(3))
                .andExpect(jsonPath("$.individualCommands[0]")
                        .value("TEK822,S12=iot.1nce.net,S13=,S14="))
                .andExpect(jsonPath("$.individualCommands[1]")
                        .value("TEK822,S15=173.212.215.131,S16=9002"))
                .andExpect(jsonPath("$.individualCommands[2]").value("TEK822,R3=ACTIVE"))
                // composedAscii: password solo sul primo, virgola tra i pezzi, CRLF in coda
                .andExpect(jsonPath("$.composedAscii")
                        .value("TEK822,S12=iot.1nce.net,S13=,S14=,S15=173.212.215.131,S16=9002,R3=ACTIVE\r\n"));
    }

    @Test
    @DisplayName("POST /api/encode con REQUEST_STATUS → no REBOOT (è R-command, non S)")
    void encode_requestStatus_noReboot() throws Exception {
        EncodeRequest req = new EncodeRequest(
                "DEV1", "TEK822V2", "TEK822",
                List.of(new EncodeRequest.CommandSpec("REQUEST_STATUS", Map.of()))
        );

        mvc.perform(post("/api/encode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.individualCommands.length()").value(1))
                .andExpect(jsonPath("$.individualCommands[0]").value("TEK822,R6=02"))
                .andExpect(jsonPath("$.composedAscii").value("TEK822,R6=02\r\n"));
    }

    @Test
    @DisplayName("POST /api/encode con SET_CONTROL2_CONFIG adcRaw=true → S26=80")
    void encode_setControl2Config_adcRaw() throws Exception {
        EncodeRequest req = new EncodeRequest(
                "DEV1", "TEK822V2", "TEK822",
                List.of(new EncodeRequest.CommandSpec("SET_CONTROL2_CONFIG",
                        Map.of("adcRaw", true)))
        );

        mvc.perform(post("/api/encode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.individualCommands[0]").value("TEK822,S26=80"));
    }

    @Test
    @DisplayName("POST /api/encode con type sconosciuto → 400 + messaggio")
    void encode_unknownType_returns400() throws Exception {
        EncodeRequest req = new EncodeRequest(
                "DEV1", "TEK822V2", "TEK822",
                List.of(new EncodeRequest.CommandSpec("UNKNOWN_CMD", Map.of()))
        );

        mvc.perform(post("/api/encode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("UNKNOWN_CMD")));
    }

    @Test
    @DisplayName("POST /api/encode con lista vuota → 400")
    void encode_emptyCommands_returns400() throws Exception {
        EncodeRequest req = new EncodeRequest(
                "DEV1", "TEK822V2", "TEK822", List.of()
        );

        mvc.perform(post("/api/encode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/encode senza password → usa default TEK822")
    void encode_defaultPassword() throws Exception {
        EncodeRequest req = new EncodeRequest(
                "DEV1", "TEK822V2", null,
                List.of(new EncodeRequest.CommandSpec("REQUEST_STATUS", Map.of()))
        );

        mvc.perform(post("/api/encode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.individualCommands[0]").value("TEK822,R6=02"));
    }
}
