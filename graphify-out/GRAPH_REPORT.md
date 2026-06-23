# Graph Report - .  (2026-06-23)

## Corpus Check
- 108 files · ~187,397 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 961 nodes · 2206 edges · 58 communities (45 shown, 13 thin omitted)
- Extraction: 82% EXTRACTED · 18% INFERRED · 0% AMBIGUOUS · INFERRED: 395 edges (avg confidence: 0.81)
- Token cost: 209,540 input · 23,280 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Worker Command Flow|Worker Command Flow]]
- [[_COMMUNITY_Persistence Layer|Persistence Layer]]
- [[_COMMUNITY_AlarmRule Pattern|AlarmRule Pattern]]
- [[_COMMUNITY_Decoder Context Impl|Decoder Context Impl]]
- [[_COMMUNITY_Decode-Tool Decode REST|Decode-Tool Decode REST]]
- [[_COMMUNITY_Config Entity & API|Config Entity & API]]
- [[_COMMUNITY_Missed Transmission Detector|Missed Transmission Detector]]
- [[_COMMUNITY_Common Decoder Interfaces|Common Decoder Interfaces]]
- [[_COMMUNITY_CommandEntry DTO|CommandEntry DTO]]
- [[_COMMUNITY_DB Schema & Persistence Tests|DB Schema & Persistence Tests]]
- [[_COMMUNITY_Architecture & Project Docs|Architecture & Project Docs]]
- [[_COMMUNITY_Common Worker Interfaces|Common Worker Interfaces]]
- [[_COMMUNITY_CommandView DTOs|CommandView DTOs]]
- [[_COMMUNITY_EventHub Producer Config|EventHub Producer Config]]
- [[_COMMUNITY_CommandService|CommandService]]
- [[_COMMUNITY_AlarmRule Tests|AlarmRule Tests]]
- [[_COMMUNITY_EventHub Publisher|EventHub Publisher]]
- [[_COMMUNITY_Decoder Smoke Tests|Decoder Smoke Tests]]
- [[_COMMUNITY_AlarmEntity Mapping|AlarmEntity Mapping]]
- [[_COMMUNITY_Decode Controller Test|Decode Controller Test]]
- [[_COMMUNITY_FlagDecoder|FlagDecoder]]
- [[_COMMUNITY_CommandRepository Tests|CommandRepository Tests]]
- [[_COMMUNITY_Encode Controller Test|Encode Controller Test]]
- [[_COMMUNITY_Tek822 Encoder|Tek822 Encoder]]
- [[_COMMUNITY_Tek822 Decoder|Tek822 Decoder]]
- [[_COMMUNITY_TimeSeries Service|TimeSeries Service]]
- [[_COMMUNITY_TCP Server|TCP Server]]
- [[_COMMUNITY_JacksonConfig|JacksonConfig]]
- [[_COMMUNITY_Hex Slicer|Hex Slicer]]
- [[_COMMUNITY_Encode REST DTOs|Encode REST DTOs]]
- [[_COMMUNITY_CommandsPacket|CommandsPacket]]
- [[_COMMUNITY_Tek822 Worker Tests|Tek822 Worker Tests]]
- [[_COMMUNITY_Decoder Smoke Test|Decoder Smoke Test]]
- [[_COMMUNITY_CheckpointStore|CheckpointStore]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 36|Community 36]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 38|Community 38]]
- [[_COMMUNITY_Community 39|Community 39]]
- [[_COMMUNITY_Community 40|Community 40]]
- [[_COMMUNITY_Community 44|Community 44]]
- [[_COMMUNITY_Community 45|Community 45]]
- [[_COMMUNITY_Community 46|Community 46]]
- [[_COMMUNITY_Community 53|Community 53]]
- [[_COMMUNITY_Community 54|Community 54]]

## God Nodes (most connected - your core abstractions)
1. `Tek822Encoder` - 34 edges
2. `Tek822DecoderTest` - 33 edges
3. `String` - 32 edges
4. `isEmpty()` - 31 edges
5. `CommandEntity` - 28 edges
6. `Tek822EncoderTest` - 28 edges
7. `Tek822Decoder` - 27 edges
8. `Test` - 26 edges
9. `DisplayName` - 26 edges
10. `Test` - 26 edges

## Surprising Connections (you probably didn't know these)
- `Beacon Architecture and Data Flow Diagram` --conceptually_related_to--> `Worker & Decoder Split Refactoring`  [INFERRED]
  Beacon architecture and data flow.png → CLAUDE.md
- `TEK822 XLSM Spec Dump` --references--> `Configuration and Command v1.21 Spec PDF`  [INFERRED]
  _xlsm_822.txt → configuration_and_command_v1.21.pdf
- `XLSM A187 Parser Script` --references--> `TEK822 XLSM Spec Dump`  [INFERRED]
  _xlsm_a187.py → _xlsm_822.txt
- `XLSM Dump Script` --shares_data_with--> `TEK822 XLSM Spec Dump`  [INFERRED]
  _xlsm_dump.py → _xlsm_822.txt
- `XLSM Msg16 Parser` --references--> `TEK822 XLSM Spec Dump`  [INFERRED]
  _xlsm_msg16.py → _xlsm_822.txt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Worker/Decoder Split Contracts** — architettura_worker_interface, architettura_decoder_interface, architettura_workercontext, architettura_decodercontext [EXTRACTED 1.00]
- **Gas Telemetry Persistence Layer** — persistence_alarmentity, persistence_commandentity, persistence_configentity [EXTRACTED 1.00]
- **Decoder contract: interface + context + output packet** — common_decoder_decoder, common_decodercontext_decodercontext, common_decodedpacket_decodedpacket, common_measure_measure, common_alarm_alarm [EXTRACTED 1.00]
- **Worker contract: interface + context + commands** — common_worker_worker, common_workercontext_workercontext, common_commandspacket_commandspacket [EXTRACTED 1.00]
- **REST decode flow: controller, request/response, views** — rest_decodecontroller_decodecontroller, rest_decoderequest_decoderequest, rest_decoderesponse_decoderesponse, rest_measureview_measureview, rest_alarmview_alarmview [EXTRACTED 1.00]
- **Threshold alarm rules** — alarm_tanklevelhighrule, alarm_tanklevellowrule, alarm_temperaturerangerule, alarm_thresholdrules [INFERRED 0.85]
- **Primary data publisher implementations** — publisher_primarydatapublisher, publisher_eventhubprimarydatapublisher, publisher_loggingprimarydatapublisher [INFERRED 0.90]
- **Command REST API** — rest_commandcontroller, dto_commandview, dto_createcommandrequest [INFERRED 0.85]
- **Decoder pipeline** — decoder_eventhubconsumer_class, impl_tek822decoder_class, impl_flagdecoder_class [INFERRED 0.85]
- **TEK822 decoder tests** — impl_tek822decodertest_class, impl_flagdecodertest_class, alarm_alarmrulestest_class [INFERRED 0.75]
- **Worker ingestion pipeline** — worker_TcpServer, worker_WorkerContextImpl, worker_CommandService [INFERRED 0.85]
- **Decoder pipeline** — decoder_EventHubConsumerConfig, decoder_DecoderContextImpl, decoder_TimeSeriesService [INFERRED 0.85]
- **Decode tool app** — decodetool_DecodeToolApplication, decodetool_DecoderBeansConfig, decodetool_index_html [INFERRED 0.75]

## Communities (58 total, 13 thin omitted)

### Community 0 - "Worker Command Flow"
Cohesion: 0.07
Nodes (31): ClaimedBatch, hasCommands(), WorkerContextImpl, WorkerContextImplTest, WorkerContext, CommandService, CommandsPacket, Override (+23 more)

### Community 1 - "Persistence Layer"
Cohesion: 0.14
Nodes (16): AlarmEntity, AlarmRepository, GetMapping, Instant, List, MeasureEntity, MeasureRepository, String (+8 more)

### Community 2 - "AlarmRule Pattern"
Cohesion: 0.06
Nodes (38): AlarmCodes, AlarmRule, BatteryLowRule, TankLevelHighRule, TankLevelLowRule, TemperatureRangeRule, ThresholdRules, Alarm (+30 more)

### Community 3 - "Decoder Context Impl"
Cohesion: 0.09
Nodes (23): DecoderContextImpl, DecoderContextImplTest, AlarmRule, ConfigRepository, DecodedPacket, List, Object, Override (+15 more)

### Community 4 - "Decode-Tool Decode REST"
Cohesion: 0.08
Nodes (21): AlarmView, DecodeRequest, PostMapping, ResponseEntity, Tek822Decoder, Alarm, Integer, List (+13 more)

### Community 5 - "Config Entity & API"
Cohesion: 0.14
Nodes (16): DeleteMapping, ConfigEntity, ConfigRepository, GetMapping, ResponseEntity, String, Void, CommandEntry (+8 more)

### Community 6 - "Missed Transmission Detector"
Cohesion: 0.09
Nodes (25): MissedTransmissionDetector, MissedTransmissionDetectorTest, CreateCommandRequest, CommandStatus, DisplayName, HttpEntity, String, T (+17 more)

### Community 7 - "Common Decoder Interfaces"
Cohesion: 0.10
Nodes (21): Alarm, DecodedPacket, Decoder, DecoderContext, Measure, TEK822 Protocol, DecodedPacket, String (+13 more)

### Community 8 - "CommandEntry DTO"
Cohesion: 0.17
Nodes (9): Object, String, CommandEntry, List, String, CommandEntry(), getParam(), getParamOrDefault() (+1 more)

### Community 9 - "DB Schema & Persistence Tests"
Cohesion: 0.10
Nodes (15): decoder schema-postgresql.sql, AlarmRepository, DecodedPacket, MeasureRepository, String, Transactional, DisplayName, Test (+7 more)

### Community 10 - "Architecture & Project Docs"
Cohesion: 0.08
Nodes (21): Alarm Record, Beacon Architecture and Data Flow Diagram, Beacon Architecture and Data Flow Diagram (variant 1), Architettura CLAUDE.md - Project Guidance, CommandsPacket Record, DecodedPacket Record, Decoder Interface, DecoderContext Interface (+13 more)

### Community 11 - "Common Worker Interfaces"
Cohesion: 0.11
Nodes (13): CommandsPacket, Worker, WorkerContext, CommandsPacket, String, CommandsPacket, Override, String (+5 more)

### Community 12 - "CommandView DTOs"
Cohesion: 0.12
Nodes (8): from(), CommandView, CommandStatus, Instant, Integer, Long, String, CommandEntity

### Community 13 - "EventHub Producer Config"
Cohesion: 0.11
Nodes (16): EventHubProducerConfig, DecoderApplication, EventHubConsumer, ErrorContext, EventContext, String, CheckpointStore, Decoder (+8 more)

### Community 14 - "CommandService"
Cohesion: 0.17
Nodes (11): CommandService, PostConstruct, PreDestroy, PrimaryDataPublisher, Worker, DisplayName, Test, InputStream (+3 more)

### Community 15 - "AlarmRule Tests"
Cohesion: 0.19
Nodes (11): AlarmRulesTest, DisplayName, String, Test, DecoderContext, DisplayName, Object, String (+3 more)

### Community 16 - "EventHub Publisher"
Cohesion: 0.10
Nodes (14): EventHubProducerClient, Override, String, Override, String, String, String, DisplayName (+6 more)

### Community 17 - "Decoder Smoke Tests"
Cohesion: 0.12
Nodes (14): EventHubConsumerConfig, DecoderApplicationSmokeTest, DecoderContext, String, DecodedPacket, Override, String, SuppressWarnings (+6 more)

### Community 18 - "AlarmEntity Mapping"
Cohesion: 0.13
Nodes (10): Alarm, Instant, Long, String, AlarmEntity, Instant, List, String (+2 more)

### Community 19 - "Decode Controller Test"
Cohesion: 0.20
Nodes (8): DisplayName, String, Test, DisplayName, Test, JsonNode, DecodeControllerTest, EncodeControllerTest

### Community 20 - "FlagDecoder"
Cohesion: 0.26
Nodes (6): List, String, DisplayName, Test, FlagDecoder, FlagDecoderTest

### Community 21 - "CommandRepository Tests"
Cohesion: 0.29
Nodes (7): CommandStatus, DisplayName, Instant, Long, String, Test, CommandRepositoryTest

### Community 22 - "Encode Controller Test"
Cohesion: 0.27
Nodes (10): Checkpoint, InMemoryCheckpointStore, CheckpointStore, Flux, List, Override, String, Void (+2 more)

### Community 23 - "Tek822 Encoder"
Cohesion: 0.17
Nodes (12): CommandEntity, CommandRepository, CommandStatus, CommandView, GetMapping, List, Long, ObjectMapper (+4 more)

### Community 24 - "Tek822 Decoder"
Cohesion: 0.31
Nodes (9): CommandEntity, CommandStatus, Instant, List, Long, Query, String, Modifying (+1 more)

### Community 25 - "TimeSeries Service"
Cohesion: 0.22
Nodes (9): EncodeRequest, ObjectMapper, PostMapping, ResponseEntity, String, Tek822Encoder, EncodeController, EncodeRequest (+1 more)

### Community 26 - "TCP Server"
Cohesion: 0.21
Nodes (7): DecoderBeansConfig, DecodeToolApplication, decode-tool index.html, Bean, Tek822Decoder, Tek822Encoder, String

### Community 27 - "JacksonConfig"
Cohesion: 0.42
Nodes (5): DisplayName, HttpEntity, String, Test, ConfigControllerTest

### Community 28 - "Hex Slicer"
Cohesion: 0.22
Nodes (5): Map, Object, ObjectMapper, PrePersist, PreUpdate

### Community 29 - "Encode REST DTOs"
Cohesion: 0.28
Nodes (5): CommandApiApplication, CommandApiApplicationSmokeTest, String, DisplayName, Test

### Community 30 - "CommandsPacket"
Cohesion: 0.29
Nodes (7): Configuration and Command Reference Text, Configuration and Command v1.21 Spec PDF, TEK822 XLSM Spec Dump, XLSM A187 Parser Script, XLSM Dump Script, XLSM Msg16 Parser, XLSM Msg17 Parser

### Community 31 - "Tek822 Worker Tests"
Cohesion: 0.53
Nodes (4): ConditionalOnMissingBean, JacksonConfig, Bean, ObjectMapper

## Knowledge Gaps
- **85 isolated node(s):** `String`, `CommandView`, `Long`, `String`, `Void` (+80 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **13 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `isEmpty()` connect `AlarmRule Tests` to `Worker Command Flow`, `Persistence Layer`, `Decoder Context Impl`, `Decode-Tool Decode REST`, `Config Entity & API`, `Common Decoder Interfaces`, `DB Schema & Persistence Tests`, `Common Worker Interfaces`, `Decoder Smoke Tests`, `FlagDecoder`, `CommandRepository Tests`, `TimeSeries Service`, `JacksonConfig`?**
  _High betweenness centrality (0.210) - this node is a cross-community bridge._
- **Why does `WorkerContextImpl` connect `Worker Command Flow` to `Decoder Context Impl`, `CommandService`?**
  _High betweenness centrality (0.079) - this node is a cross-community bridge._
- **Why does `Tek822Decoder` connect `Common Decoder Interfaces` to `Persistence Layer`, `Config Entity & API`, `FlagDecoder`, `EventHub Producer Config`?**
  _High betweenness centrality (0.069) - this node is a cross-community bridge._
- **Are the 28 inferred relationships involving `isEmpty()` (e.g. with `.batteryLow_noTrigger()` and `.tankLow_ignoresOtherObis()`) actually correct?**
  _`isEmpty()` has 28 INFERRED edges - model-reasoned connections that need verification._
- **What connects `String`, `CommandView`, `Long` to the rest of the system?**
  _85 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Worker Command Flow` be split into smaller, more focused modules?**
  _Cohesion score 0.06885245901639345 - nodes in this community are weakly interconnected._
- **Should `Persistence Layer` be split into smaller, more focused modules?**
  _Cohesion score 0.13836477987421383 - nodes in this community are weakly interconnected._