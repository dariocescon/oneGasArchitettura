# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Location

This `Architettura/` folder contains architecture documentation and diagrams.

- `../oneGasMeterNew/` — DLMS/COSEM server for Italian gas meters (UNI/TS 11291)
- `../onGas_Meteor_claude/` — TEK822 meter server (READ-ONLY reference: source for the Worker/Decoder split)

## Active Work: Worker & Decoder Split

The codebase in `onGas_Meteor_claude` is being refactored into two separate components. **Do not modify `onGas_Meteor_claude`** — it is the reference implementation only.

### Approved interfaces

```java
// Worker: receives raw bytes, verifies, decrypts, publishes to Event Hub, manages commands
public interface Worker {
    default boolean validate(byte[] data) { return true; }
    void doWork(WorkerContext ctx, byte[] data);
}
public record CommandsPacket(int handle, String[] commands) {}
public interface WorkerContext {
    void publishPrimaryData(String deviceId, byte[] data); // → Azure Event Hub
    CommandsPacket getCommands(String deviceId);           // atomic: marks IN_PROGRESS
    void markCommandsSent(int handle);                     // status → SENT (not delete)
}

// Decoder: decodes raw bytes → typed measures + alarms, publishes with original timestamp
public interface Decoder {
    void doDecode(DecoderContext ctx, String deviceId, byte[] data);
}
public record Measure(Instant timestamp, String obisCode, double value, String unit) {}
public record Alarm(Instant timestamp, String alarmCode, String description) {}
public record DecodedPacket(List<Measure> measures, List<Alarm> alarms) {}
public interface DecoderContext {
    <T> T getConfig(String cfgKey);
    <T> T getConfig(String deviceId, String cfgKey);
    void publishDecodedData(String deviceId, DecodedPacket packet); // → TimeScaleDB
}
```

### Key decisions
- `markCommandsSent` (not `delCommands`): commands are marked SENT, never deleted
- Timestamp reconstruction (RTC + server date + midnight wrap) stays inside the Decoder
- `Object[] measures` → `DecodedPacket` typed record
- UDP case: `Worker` + `Decoder` collapse into a single class implementing both interfaces
- Config-based alarms (`getConfig`) are new features, no existing code to port

### Mapping from onGas_Meteor_claude
| Existing class | New component |
|---|---|
| `TcpConnectionHandler` | Transport layer (calls `worker.doWork()`) |
| `TelemetryService` (commands part) | `Tek822Worker` + `WorkerContextImpl` |
| `TekMessageDecoder` + `MessageTypeParser` | `Tek822Decoder.doDecode()` |
| `Tek822Encoder` | stays inside `Tek822Worker` |
| `TelemetryService` (orchestration) | dissolved |

## Project Overview

**OneGasMeterNew** is a Java 21 Spring Boot server that collects remote meter readings from Italian gas meters via the DLMS/COSEM protocol. It conforms to **UNI/TS 11291** (Italian standard for gas meter telemetry).

- TCP server on port 60103 (meter push connections)
- UDP server on port 60104 (DATA-NOTIFICATION with GBT reassembly)
- SQL Server database for persistence
- AES-256-GCM encrypted meter keys, HIGH_GMAC (HLS5) DLMS authentication

## Build & Run

```bash
cd ../oneGasMeterNew

# Build
./mvnw clean package

# Run tests
./mvnw test

# Run single test class
./mvnw test -Dtest=CompactFrameParserTest

# Start application
./mvnw spring-boot:run
# or
java -jar target/oneGasMeterNew-0.0.1-SNAPSHOT.jar
```

Required environment variable for production:
```bash
export ONEGASMETER_KEY_MASTER_PASSWORD=<your-aes-master-key>
```

Test database is H2 in MSSQLServer compatibility mode — no external DB needed for tests.

## Architecture

### Session Flow

```
Gas Meter (DLMS WRAPPER over TCP/UDP)
    ↓
TcpServer (port 60103) / UdpServer (port 60104)
    ↓  [1 Java 21 virtual thread per connection, semaphore-limited to 10,000]
MeterSessionHandler
    ├─ DlmsMeterClient  →  AARQ/AARE handshake, read COSEM objects
    ├─ TelemetryService →  persist meter readings to SQL Server
    └─ CommandService   →  execute pending commands, then close session
```

### Package Layout (`com.aton.proj.oneGasMeter`)

| Package | Responsibility |
|---------|---------------|
| `server/` | `TcpServer`, `UdpServer`, `MeterSessionHandler` — network layer, virtual thread pool |
| `dlms/` | `DlmsMeterClient`, `DlmsTransport`, `IncomingTcpTransport` — Gurux DLMS wrapper |
| `cosem/` | `CosemObject` (95 OBIS codes), `CompactFrameParser` (13 frame types CF3–CF51), `EventCode` (181 codes), `ValveStatus` |
| `config/` | Spring configuration beans for TCP/UDP/DLMS settings |
| `entity/` | JPA entities: `TelemetryData`, `DeviceRegistry`, `DeviceCommand`, `SessionLog` |
| `repository/` | Spring Data JPA repositories |
| `service/` | `TelemetryService`, `CommandService`, `KeyEncryptionService` (AES-256-GCM) |
| `exception/` | `DlmsCommunicationException` |

### Key Technical Details

- **Compact frames**: 13 types (CF3–CF9, CF22, CF41, CF47–CF49, CF51) — each has a distinct parsing path in `CompactFrameParser`
- **System title**: extracted from DLMS frames to look up the meter's encrypted key in `DeviceRegistry`
- **GBT segmentation**: UDP server reassembles multi-block General Block Transfer packets before processing
- **DDL**: `spring.jpa.hibernate.ddl-auto=none` — schema must exist before startup; never auto-generated

## Documentation

Detailed technical references are in `../oneGasMeterNew/documentation/`:
- `doc.md` — database schema, COSEM objects, compact frame encoding, session flow
- `documentazione.md` — Italian-language architecture overview
- `normativa_UNITS_11291_riepilogo.md` — regulatory standard summary
- `Communication_Examples/` — sample DLMS protocol exchanges

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
