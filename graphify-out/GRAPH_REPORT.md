# Graph Report - Architettura  (2026-06-23)

## Corpus Check
- 90 files · ~178,950 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 903 nodes · 1944 edges · 53 communities (46 shown, 7 thin omitted)
- Extraction: 84% EXTRACTED · 16% INFERRED · 0% AMBIGUOUS · INFERRED: 308 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]
- [[_COMMUNITY_Community 20|Community 20]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 32|Community 32]]
- [[_COMMUNITY_Community 33|Community 33]]
- [[_COMMUNITY_Community 34|Community 34]]
- [[_COMMUNITY_Community 41|Community 41]]
- [[_COMMUNITY_Community 42|Community 42]]
- [[_COMMUNITY_Community 43|Community 43]]
- [[_COMMUNITY_Community 44|Community 44]]
- [[_COMMUNITY_Community 45|Community 45]]
- [[_COMMUNITY_Community 46|Community 46]]
- [[_COMMUNITY_Community 47|Community 47]]

## God Nodes (most connected - your core abstractions)
1. `isEmpty()` - 30 edges
2. `Tek822DecoderTest` - 29 edges
3. `CommandEntity` - 28 edges
4. `Test` - 23 edges
5. `DisplayName` - 23 edges
6. `Tek822Encoder` - 21 edges
7. `Tek822Decoder` - 20 edges
8. `String` - 19 edges
9. `MeasureEntity` - 14 edges
10. `AlarmRulesTest` - 13 edges

## Surprising Connections (you probably didn't know these)
- None detected - all connections are within the same source files.

## Import Cycles
- None detected.

## Communities (53 total, 7 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.10
Nodes (21): hasCommands(), WorkerContextImplTest, CommandRepository, List, Long, ObjectMapper, String, Transactional (+13 more)

### Community 1 - "Community 1"
Cohesion: 0.06
Nodes (30): from(), CommandEntity, CommandView, CommandStatus, Instant, Integer, Long, Map (+22 more)

### Community 2 - "Community 2"
Cohesion: 0.07
Nodes (32): BatteryLowRule, TankLevelHighRule, TankLevelLowRule, TemperatureRangeRule, ThresholdRules, AlarmRule, Alarm, DecoderContext (+24 more)

### Community 3 - "Community 3"
Cohesion: 0.09
Nodes (25): MissedTransmissionDetector, MissedTransmissionDetectorTest, CreateCommandRequest, CommandStatus, DisplayName, HttpEntity, String, T (+17 more)

### Community 4 - "Community 4"
Cohesion: 0.14
Nodes (16): AlarmEntity, AlarmRepository, GetMapping, Instant, List, MeasureEntity, MeasureRepository, String (+8 more)

### Community 5 - "Community 5"
Cohesion: 0.10
Nodes (14): AlarmRepository, DecodedPacket, MeasureRepository, String, Transactional, DisplayName, Test, Instant (+6 more)

### Community 6 - "Community 6"
Cohesion: 0.10
Nodes (21): DecoderContextImpl, DecoderApplicationSmokeTest, DecoderContext, DecodedPacket, Override, String, SuppressWarnings, T (+13 more)

### Community 7 - "Community 7"
Cohesion: 0.15
Nodes (16): DeleteMapping, ConfigEntity, ConfigRepository, GetMapping, ResponseEntity, String, Void, CommandEntry (+8 more)

### Community 8 - "Community 8"
Cohesion: 0.20
Nodes (9): Object, String, CommandEntry, List, String, CommandEntry(), getParam(), getParamOrDefault() (+1 more)

### Community 9 - "Community 9"
Cohesion: 0.06
Nodes (34): 1. Tipi di messaggi, 2.1 Forma generale, 2. Composizione del messaggio, 3.1 Byte 3 — Contact Reason (PDF §2.2.1.2), 3.2 Byte 4 — Alarm/Status (PDF §2.2.1.3) ⚠️ critico per gli allarmi, 3.3 Byte 6 — Battery/Status (PDF §2.2.1.4), 3. Header comune (byte 0–16), 4.1.1 Logger Speed (byte 23) (+26 more)

### Community 10 - "Community 10"
Cohesion: 0.21
Nodes (10): Decoder, Alarm, DecodedPacket, DecoderContext, Instant, List, Measure, Override (+2 more)

### Community 11 - "Community 11"
Cohesion: 0.16
Nodes (11): CommandService, PostConstruct, PreDestroy, PrimaryDataPublisher, Worker, DisplayName, Test, InputStream (+3 more)

### Community 12 - "Community 12"
Cohesion: 0.14
Nodes (13): ClaimedBatch, DecoderContextImplTest, BeforeEach, DisplayName, Test, DisplayName, Test, ConfigEntity (+5 more)

### Community 13 - "Community 13"
Cohesion: 0.20
Nodes (9): CommandsPacket, Override, String, WorkerContext, DisplayName, Test, Tek822Worker, Tek822WorkerTest (+1 more)

### Community 14 - "Community 14"
Cohesion: 0.15
Nodes (8): Instant, Override, PrePersist, PreUpdate, String, ConfigEntity, ConfigKey, Serializable

### Community 15 - "Community 15"
Cohesion: 0.08
Nodes (20): EventHubConsumerConfig, EventHubProducerConfig, EventHubProducerClient, Bean, CheckpointStore, Bean, PreDestroy, PrimaryDataPublisher (+12 more)

### Community 16 - "Community 16"
Cohesion: 0.13
Nodes (18): Checkpoint, InMemoryCheckpointStore, CheckpointStore, EventHubConsumer, ErrorContext, EventContext, Flux, List (+10 more)

### Community 17 - "Community 17"
Cohesion: 0.19
Nodes (5): Alarm, Instant, Long, String, AlarmEntity

### Community 18 - "Community 18"
Cohesion: 0.13
Nodes (13): Active Work: Worker & Decoder Split, Approved interfaces, Architecture, Build & Run, Documentation, graphify, Key decisions, Key Technical Details (+5 more)

### Community 19 - "Community 19"
Cohesion: 0.17
Nodes (13): AlarmRulesTest, DecodedPacket, DisplayName, String, Test, DecoderContext, DisplayName, Object (+5 more)

### Community 20 - "Community 20"
Cohesion: 0.24
Nodes (8): WorkerContextImpl, CommandService, CommandsPacket, Override, PrimaryDataPublisher, String, OutputStream, WorkerContext

### Community 21 - "Community 21"
Cohesion: 0.42
Nodes (5): DisplayName, HttpEntity, String, Test, ConfigControllerTest

### Community 22 - "Community 22"
Cohesion: 0.39
Nodes (6): AlarmRule, Alarm, DecoderContext, List, Measure, String

### Community 23 - "Community 23"
Cohesion: 0.29
Nodes (3): WorkerContext, CommandsPacket, String

### Community 24 - "Community 24"
Cohesion: 0.33
Nodes (4): DecoderContext, DecodedPacket, String, T

### Community 25 - "Community 25"
Cohesion: 0.38
Nodes (5): AlarmEntity, Instant, List, String, AlarmRepository

### Community 26 - "Community 26"
Cohesion: 0.53
Nodes (4): ConditionalOnMissingBean, JacksonConfig, Bean, ObjectMapper

### Community 27 - "Community 27"
Cohesion: 0.60
Nodes (3): CommandApiApplicationSmokeTest, DisplayName, Test

### Community 28 - "Community 28"
Cohesion: 0.40
Nodes (3): Decoder, DecoderContext, String

### Community 41 - "Community 41"
Cohesion: 0.15
Nodes (13): AlarmView, DecodeRequest, PostMapping, ResponseEntity, Tek822Decoder, Alarm, Integer, List (+5 more)

### Community 42 - "Community 42"
Cohesion: 0.25
Nodes (6): List, String, DisplayName, Test, FlagDecoder, FlagDecoderTest

### Community 43 - "Community 43"
Cohesion: 0.22
Nodes (5): String, DisplayName, Test, PayloadParser, PayloadParserTest

### Community 44 - "Community 44"
Cohesion: 0.22
Nodes (11): CommandRepository, CommandStatus, CommandView, GetMapping, List, Long, ObjectMapper, PostMapping (+3 more)

### Community 45 - "Community 45"
Cohesion: 0.38
Nodes (5): DisplayName, String, Test, JsonNode, DecodeControllerTest

### Community 46 - "Community 46"
Cohesion: 0.60
Nodes (3): DecoderBeansConfig, Bean, Tek822Decoder

## Knowledge Gaps
- **96 isolated node(s):** `String`, `CommandView`, `Long`, `String`, `Void` (+91 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **7 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `isEmpty()` connect `Community 19` to `Community 0`, `Community 1`, `Community 4`, `Community 5`, `Community 6`, `Community 7`, `Community 42`, `Community 10`, `Community 12`, `Community 13`, `Community 43`, `Community 20`, `Community 21`?**
  _High betweenness centrality (0.174) - this node is a cross-community bridge._
- **Why does `Profile` connect `Community 15` to `Community 16`, `Community 3`?**
  _High betweenness centrality (0.028) - this node is a cross-community bridge._
- **Are the 27 inferred relationships involving `isEmpty()` (e.g. with `.batteryLow_noTrigger()` and `.tankLow_ignoresOtherObis()`) actually correct?**
  _`isEmpty()` has 27 INFERRED edges - model-reasoned connections that need verification._
- **What connects `String`, `CommandView`, `Long` to the rest of the system?**
  _96 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.10042283298097252 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.06254272043745727 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.06866002214839424 - nodes in this community are weakly interconnected._