# Protocollo di comunicazione Tekelek TEK822

Documento di riferimento per i logger Tekelek **TEK822** (varianti V1, V1 BTN, V2 NB-IoT/CAT-M1).
Redatto a partire da:

- `configuration_and_command_v1.21.pdf` (Logger NB-IoT/CAT-M1 User Manual, codice doc 9-5988-07)
- `configuration_and_command_v1.21.xlsm` (sheet `822` e `822 CC`, esempi operativi e formule di encoding)

In caso di conflitto tra i due, **prevale l'XLSM** (contiene esempi reali su firmware aggiornati;
il PDF mostra esempi datati su byte 0 = `0x08`, mentre nell'XLSM e nei device attuali è `0x18`).

---

## 1. Tipi di messaggi

Il device può inviare al server **sette tipi** di messaggi. Tutti condividono lo stesso header di
17 byte, ma il body cambia per tipo. Il tipo è identificato dai 6 bit meno significativi del **byte
15** dell'header.

| Tipo | Direzione | Trigger di invio | Body | Frequenza tipica |
|---|---|---|---|---|
| **#4** | device → server | Schedule (registro S2) | binary, fino a 28 misure × 4 byte | dipende da S0/S2: tipicamente ogni 1–24 h |
| **#8** | device → server | Allarme attivato oppure attivazione manuale via magnete | binary, **sempre 10 misure** (sampling buffer) | sporadico |
| **#9** | device → server | Offset timer scaduto dopo trigger di S7 (alarm dinamico 1) | binary, sampling buffer | sporadico |
| **#6** | device → server | Risposta a comando `R1=02/04/08` | ASCII, dump registri S | on demand |
| **#16** | device → server | Risposta a comando `R6=02` | ASCII CSV, statistiche cumulative | on demand o mensile (S3 bit 0) |
| **#17** | device → server | Risposta a comando `R7=<timeout>` | ASCII CSV, dati GPS | on demand |
| **#2** | device → server | Risposta a comando `R6=01` | binary, dati diagnostici | on demand |

> **Nota su Msg #4 vs #8**: il **#4** è il "delta upload" — contiene solo le misure cambiate dall'ultimo
> invio (logger buffer, fino a 28 posizioni × 15 min). Il **#8** invece scarica **sempre** le 10
> misure più recenti del **sampling buffer** (frequenza più alta, fino a 1/min) perché sono quelle
> che hanno innescato l'allarme.

> **Nota su Msg #2**: nel codice attuale del decoder *non è gestito* — l'XLSM mostra solo
> `#REF!` nelle celle. Non è una funzionalità attiva.

Cfr. [diagrammi/protocollo-tek822-flusso-decodifica.mmd](diagrammi/protocollo-tek822-flusso-decodifica.mmd)
per il flusso completo di smistamento.

---

## 2. Composizione del messaggio

### 2.1 Forma generale

```
+-----------------------+--------------------------+-----+
|   Header (17 byte)    |   Body (lunghezza var.)  | CRC |
|   byte 0 … byte 16    |   byte 17 … byte N-3     |  2  |
+-----------------------+--------------------------+-----+
```

- L'intero payload arriva sul canale TCP/UDP del worker come **byte binari grezzi**.
- Gli ultimi **2 byte** sono il **CRC** del messaggio (polinomio non documentato da Tekelek;
  la validazione non è implementata — si confida nell'integrità del layer TCP).
- La **lunghezza del body** è dichiarata nei byte 15+16: `((byte15 >> 6) & 0x03) × 256 + byte16`
  (vedi §3). La formula è centralizzata nel metodo statico
  `Tek822Decoder.computeDeclaredBodyLength(byte[])` per non duplicarla nei consumer.
  > **Caveat storico**: il PDF v1.21 cita erroneamente "Bit5&Bit4 of byte 15", ma quei bit
  > fanno parte del Message Type (msgType usa bits[5:0]); la formula coerente con i message
  > type 16/17 (che usano bit 4) usa **bits[7:6]**. Bug fixato — vedi `Tek822DecoderTest.declaredBodyLength_msgType16_noShiftConflict`.

Cfr. [diagrammi/protocollo-tek822-struttura-messaggio.mmd](diagrammi/protocollo-tek822-struttura-messaggio.mmd).

---

## 3. Header comune (byte 0–16)

Lo stesso layout vale per *ogni* tipo di messaggio.

| Offset | Nome | Decodifica | Esempio |
|---|---|---|---|
| 0 | Product Type | `0x08`=TEK822 V1, `0x17`=TEK822 V1 BTN, `0x18`=TEK822 V2 NB. Modem dedotto dal byte 1 | `0x18` → "TEK822 V2 NB" |
| 1 | HW Revision | bits[2:0] = minor, bits[7:3] = major. Display: "minor.major". Modem da major: 0=BG96, 1=BG95 | `0x02` → "2.0" + "BG96" |
| 2 | FW Revision | bits[4:0] = FW Major, bits[7:5] = FW Minor. Display: "FW major.minor" | `0xC4` → "FW 4.6"; `0x82` → "FW 2.4" |
| 3 | **Contact Reason** | bitmask, vedi §3.1 | `0x41` → Scheduled + DynLim |
| 4 | **Alarm/Status** | bitmask, vedi §3.2 | `0x89` → Active + Bund + Limit 1 |
| 5 | CSQ / RSSI | intero unsigned 0–31 | `0x19` → 25 |
| 6 | Battery/Status | vedi §3.3 | `0x36` → batt 70.9%, RTC set |
| 7–14 | IMEI | 8 byte BCD (2 cifre per byte) → stringa 16 caratteri (il leading `0` non significativo) | `08 64 43 10 47 98 70 54` → `0864431047987054` |
| 15 | **Message Type + length hi** | bits[5:0] = msgType, bits[7:6] = length high | `0x04` → Msg #4 |
| 16 | **length lo** | byte basso della lunghezza del body | `0x7B` → 123 byte body |

### 3.1 Byte 3 — Contact Reason (PDF §2.2.1.2)

Bitmask del motivo per cui il device sta inviando il messaggio. Può avere più bit set
contemporaneamente.

| Bit | Significato | Quando si attiva |
|---|---|---|
| 0 | Scheduled | invio programmato da S2 |
| 1 | Alarm | allarme statico (S4/S5/S6) attivato |
| 2 | Server Request | risposta a comando R1 |
| 3 | Manual | attivazione con magnete |
| 4 | Reboot | dopo riavvio device |
| 5 | TSP Requested | richiesta dal transponder (engineering) |
| 6 | DynLim | allarme dinamico 1 (S7) attivato |
| 7 | DynLim2 | allarme dinamico 2 (S8) attivato |

### 3.2 Byte 4 — Alarm/Status (PDF §2.2.1.3) ⚠️ critico per gli allarmi

Bitmask dello stato degli allarmi che il **device** ha diagnosticato a bordo.

| Bit | Significato | Soglia configurata su |
|---|---|---|
| 0 | Limit 1 superato | registro S4 |
| 1 | Limit 2 superato | registro S5 |
| 2 | Limit 3 superato | registro S6 |
| 3 | **Bund Status** | switch hardware dello sportellino del bund |
| 4–6 | riservati | — |
| 7 | Active | flag di device attivo (NON è un allarme) |

Vedi §5 per la pipeline di generazione `Alarm` server-side.

### 3.3 Byte 6 — Battery/Status (PDF §2.2.1.4)

| Bit | Significato | Formula |
|---|---|---|
| 0–4 | Battery Percentage | `% = (bits[4:0] × 100) / 31` |
| 5 | RTC Set | 1 = RTC sincronizzato |
| 6 | LTE Act | 1 = registrato su LTE (CAT-M); 0 = 2G |
| 7 | riservato | — |

Esempio: `0x36 = 0b00110110` → bits[4:0] = `0b10110` = 22 → batteria 70.9%; bit 5 = 1 (RTC OK);
bit 6 = 0 (2G).

---

## 4. Decodifica per tipo di messaggio

### 4.1 Msg #4 / #8 / #9 — Misure (binary)

Layout dopo l'header:

| Offset | Nome | Decodifica |
|---|---|---|
| 17–18 | Message Count | `byte17 × 256 + byte18` — counter incrementale per device |
| 19 | Try Tickets + RTC Hours | bits[7:5] = retry rimasti, bits[4:0] = ore RTC (0–23) |
| 20–21 | Energy Used (FW > 3.0) / Last Error Code (FW 3.0) | uint16 BE, mAh |
| 22 | Network Tech | bit 7 = 1 → NB-IoT; (bit 7 = 0 + byte 6 bit 6 = 1) → CAT-M; altrimenti 2G |
| 23 | **Logger Speed** | regola a 3 casi (vedi §4.1.1) |
| 24 | Login Time | secondi = `byte24 × 5` |
| 25 | RTC Minutes | minuti 0–59 |
| 26+ | Data 0 … Data 27 | fino a 28 misure × 4 byte ciascuna |
| ultimi 2 | CRC | non validato |

#### 4.1.1 Logger Speed (byte 23)

Definisce il delta temporale tra una misura e la successiva. Regola dal PDF §2.2.2.1:

| Valore byte 23 | Intervallo |
|---|---|
| `0x00` | 1 minuto |
| `0x80` | 15 minuti |
| altrimenti | `(byte & 0x7F) × 15` minuti |

#### 4.1.2 Ricostruzione del timestamp

Il device invia solo `hh:mm`. Il server aggiunge la data corrente. **Midnight wrap**: se l'RTC del
device è > 12h avanti rispetto al server (es. RTC=23:50, server=00:05), si assume sia "ieri".

Il timestamp della misura `i` (con `i=0` = più recente) è:
```
ts[i] = baseTimestamp − i × loggerSpeedSec
```

#### 4.1.3 Decodifica di una singola misura (4 byte)

Sia `j = 26 + i × 4`. La misura `i` ha la struttura:

| Byte | Nome | Decodifica | Valore atteso |
|---|---|---|---|
| `j` | Aux2 | `data[j] & 0xFF` (intero unsigned) | sanity check: dovrebbe essere 10 |
| `j+1` | Temperature raw | **`°C = (data[j+1] & 0xFF) / 2 − 30`** | range nominal −30 … +97.5 °C |
| `j+2` | Aux1 + distance hi | bits[5:2] = Aux1 (atteso 10), bits[1:0] = distance high | — |
| `j+3` | distance lo | byte basso della distanza | — |

La **distanza** (o lettura sensore generica) è un valore a 10 bit:
```
distance = ((data[j+2] & 0x03) << 8) | (data[j+3] & 0xFF)   // range 0–1023
```

> **Attenzione all'unità di misura**: l'unità della "distanza" **non è cm** in senso assoluto.
> Dipende dai bit 5/6/7 del registro **S26** del device:
> - bit 7 = 1 → Raw ADC 0–1023
> - bit 7 = 0, bit 6 = 1 → percentuale alta risoluzione 0–1000
> - bit 7 = 0, bit 6 = 0 → percentuale bassa risoluzione 0–100
>
> Per applicazioni LPG tank è tipicamente **percentuale**, non centimetri. Il nome
> `distance_cm` nel decoder è in attesa di rinomina (vedi [bug-aperti.md](bug-aperti.md) §1).

Una misura con tutti e 4 i byte a `0x00` è uno **slot vuoto** — il decoder la salta.

#### 4.1.4 Esempio decodificato (XLSM sheet "822", riga 7)

Payload esempio Msg #4:
```
18 02 03 41 89 19 36 0864431047987054 04 7B 0932 48 000B FF 81 03 00
0A682BFE 0A682BFE 0A6A2BFE 0A6A2BFE 0A6A2BFE 0A6A2800 0A6A2BFE 0A6A2843
0A6A281E 0A6A2862 0A6A2B70 0A6A2BFE
000000…000000 (16 slot vuoti)
2F1D (CRC)
```

Decodifica della prima misura (`0A 68 2B FE`):
- Aux2 = `0x0A` = 10 (OK, sanity check)
- Temp = `0x68` / 2 − 30 = 104 / 2 − 30 = **22 °C**
- Aux1 = `(0x2B >> 2) & 0x0F` = `0b001010` & `0x0F` = 10 (OK, sanity check)
- Distance = `((0x2B & 0x03) << 8) | 0xFE` = `(0b11 << 8) | 254` = 768 + 254 = **1022**

---

### 4.2 Msg #6 — Settings (ASCII)

Il body è una **stringa ASCII** con i valori dei registri S separati da virgole.

Esempio:
```
S0=80,S1=05,S2=7F0038,S3=01,S4=081E,S5=8832,S6=8846,S7=00,S8=00,
S9=+353861756364,S10=,S11=TEK822,S12=stream.co.uk,S13=streamip,S14=streamip,
S15=84.51.250.104,S16=9000,S17=7200,S18=C8,S19=00,S20=00,S21=,S22=,
S23=13,S24=00,S25=00,S26=10
```

**Regola fondamentale di decodifica**: i valori dei registri "numerici" sono **in esadecimale**, non in decimale.

| Setting | Valore stringa | Decodifica corretta |
|---|---|---|
| `S0=80` | `"80"` | `0x80` = **128** (bit 7 = sampling 15min, bit 2 = logger speed) |
| `S2=7F0038` | `"7F0038"` | `0x7F0038` = **8 323 128** (multi-byte: giorni settimana + ora) |
| `S22=181A` | `"181A"` | `0x181A` = **6170** (LTE band code) |

**Strategia di preservazione**: il decoder emette **una `Measure` per ogni `Sx=value`** trovato,
indipendentemente dal tipo. Pattern:

| Tipo di registro | Esempio | `value` | `unit` |
|---|---|---|---|
| Hex-parseable | `S0=81` | `129` (`0x81`) | `"81"` (raw hex string) |
| ASCII non-hex | `S12=iot.1nce.net` | `0` | `"iot.1nce.net"` |
| Vuoto | `S9=` | `0` | `""` |

Risultato: tutti i 30 registri (S0..S29) di un dump risposta a `R1=02` finiscono in `device_measures`
con obisCode `setting.S0` ... `setting.S29`. Nessuna perdita di info — anche APN, IP, password
restano queryable via SQL (filtrando `WHERE obis_code = 'setting.S12'`).

> **CRC trailer**: anche per Msg #6 gli ultimi 2 byte del payload sono il CRC, **fuori** dal body
> ASCII. Il decoder lo esclude correttamente prima di parsare la stringa.

---

### 4.3 Msg #16 — Statistiche (ASCII CSV)

Body ASCII con campi separati da `,`, preceduto da una virgola iniziale.

Esempio decodificato:
```
,89882390000028895236,19875,38,40,13612,0,91131,18004,12,240619,14360,28
```

Il decoder emette **tutti e 12 i campi** come `Measure` (prima del fix Msg#16 ne emetteva 7).
ICCID è una stringa numerica a 20 cifre, non rappresentabile in `double` senza perdita di precisione,
quindi viene preservata in `unit` (con `value = 0`) come per i setting ASCII.

| Pos | Campo | obisCode emesso | Unità | Note |
|---|---|---|---|---|
| 0 | ICCID | `stats.iccid` | (string in `unit`) | 20 cifre, non-numeric |
| 1 | Energy Used to Date | `stats.energy_used_ma_minutes` | mA·min | rinominato (era `_mah` sbagliato) |
| 2 | Min Temp | `stats.min_temperature_c` | °C | — |
| 3 | Max Temp | `stats.max_temperature_c` | °C | — |
| 4 | Message Count | `stats.message_count` | — | counter cumulativo |
| 5 | Count of Delivery Fail | `stats.delivery_fail` | — | — |
| 6 | Total Send Time | `stats.total_send_time_s` | s | per media: `total / message_count` |
| 7 | Max Send Time | `stats.max_send_time_s` | s | — |
| 8 | Min Send Time | `stats.min_send_time_s` | s | — |
| 9 | RSSI Total | `stats.rssi_total` | — | per media: `total / rssi_valid_count` |
| 10 | RSSI Valid Readings | `stats.rssi_valid_count` | — | — |
| 11 | RSSI Failed Readings | `stats.rssi_fail_count` | — | — |

---

### 4.4 Msg #17 — GPS (ASCII CSV)

Body ASCII analogo al #16. Esempio:
```
,95,134442.0,5255.9950N,00832.4417W,1.9,127.8,2,0.00,0.0,0.0,021015,04
```

Il decoder emette **tutti e 12 i campi** come `Measure` (prima del fix Msg#17 ne emetteva 4).
Le coordinate sono in formato NMEA (gradi-minuti decimali con suffisso N/S/E/W) e non sono
rappresentabili come `double` → preservate in `unit` con `value=0`. Stesso pattern per UTC e Date.

| Pos | Campo | obisCode emesso | Tipo | Esempio |
|---|---|---|---|---|
| 0 | GPS Time to Fix | `gps.time_to_fix_s` | numeric (s) | `95` |
| 1 | UTC time | `gps.utc` | string in `unit` | `"134442.0"` (`hhmmss.s`) |
| 2 | Latitude | `gps.latitude` | string in `unit` | `"5255.9950N"` (NMEA `ddmm.mmmm N\|S`) |
| 3 | Longitude | `gps.longitude` | string in `unit` | `"00832.4417W"` (NMEA `dddmm.mmmm E\|W`) |
| 4 | Horizontal precision (HDOP) | `gps.hdop` | numeric (0.5-99.9) | `1.9` |
| 5 | Altitude | `gps.altitude_m` | numeric (m) | `127.8` |
| 6 | GNSS positioning mode | `gps.fix_mode` | numeric | `2` = 2D, `3` = 3D |
| 7 | Ground heading | `gps.heading_deg` | numeric (°) | `0.00` |
| 8 | Speed over ground (km/h) | `gps.speed_kmh` | numeric (km/h) | `0.0` |
| 9 | Speed over ground (nodi) | `gps.speed_knots` | numeric (knots) | `0.0` |
| 10 | Date | `gps.date` | string in `unit` | `"021015"` (`ddmmyy`) |
| 11 | Numero satelliti | `gps.satellites` | numeric (0-12) | `4` |

---

## 5. Allarmi

Esistono **due famiglie distinte** di allarmi nella pipeline:

### 5.1 Allarmi auto-segnalati dal device (byte 4 dell'header)

Il device confronta autonomamente le misure con le soglie configurate sui propri registri
**S4/S5/S6** (statiche) e **S7/S8** (dinamiche). Quando una soglia viene superata, alza i bit
corrispondenti nel **byte 4** del messaggio in uscita (vedi §3.2) e tipicamente invia un **Msg #8**
(o #9 se è scattato S7 e poi S29 ha avviato l'offset timer).

Il decoder genera un `Alarm` distinto per ciascun bit del byte 4:

| Bit byte 4 | Codice `Alarm` generato | Soglia |
|---|---|---|
| 0 | `DEVICE_LIMIT_1` | S4 |
| 1 | `DEVICE_LIMIT_2` | S5 |
| 2 | `DEVICE_LIMIT_3` | S6 |
| 3 | `DEVICE_BUND_STATUS` | switch hardware |
| 7 | nessuno (Active = info, non allarme) | — |

> **Comportamento globale per tipo di messaggio**: la generazione di questi allarmi e l'estrazione
> di `contact_reason_flags`/`alarm_status_flags` avvengono in `Tek822Decoder.doDecode` **prima del
> dispatcher per Msg type**. Pertanto un byte 4 con bit settati produce `Alarm` anche su Msg #6 /
> #16 / #17 — l'header byte 0-6 è condiviso.

Per analisi a posteriori delle bitmask sono esposti come `Measure` diagnostiche:
- `contact_reason_flags` = `byte3 & 0xFF` (valore decimale)
- `alarm_status_flags` = `byte4 & 0xFF` (valore decimale)

Inoltre il modulo `FlagDecoder` (in `decoder.impl`) traduce le due bitmask in liste di label
umani (es. `byte 0x89` → `["Limit1", "Bund", "Active"]`). Le liste sono esposte dal decode-tool
nella response REST (`contactReasonFlags`, `alarmStatusFlags`).

> **Importante**: questi allarmi non dipendono dalla nostra configurazione server-side, ma dalle
> soglie effettivamente settate sui registri del device (interrogabili via Msg #6). Possono essere
> diversi dagli allarmi calcolati server-side e quindi vanno trattati come **fonte primaria**.

### 5.2 Allarmi calcolati server-side (`AlarmRule` + scheduler)

Sono regole Java aggiuntive eseguite sul packet decodificato. Configurazione via tabella
`device_config` (vedi `DecoderContext.getConfig`).

| Codice `Alarm` | Sorgente | Soglia |
|---|---|---|
| `TANK_LEVEL_LOW` | misura `distance_cm` | `alarm.tank.low_threshold` (default 20) |
| `TANK_LEVEL_HIGH` | misura `distance_cm` | `alarm.tank.high_threshold` (default 90) |
| `BATTERY_LOW` | misura `battery_voltage` *(non ancora estratta dal decoder)* | `alarm.battery.low_threshold` (default 3200 mV) |
| `TEMPERATURE_OUT_OF_RANGE` | misura `temperature_c` | `alarm.temperature.min/max` (default −10 … +60 °C) |
| `MISSED_TRANSMISSION` | scheduler `MissedTransmissionDetector` | `alarm.missed_transmission.threshold_seconds` (default 24 h) |

Il `MissedTransmissionDetector` è un job `@Scheduled` (default ogni 5 min) che confronta
`now() − lastTimestamp` con la soglia. Ha anti-flood: non riemette l'allarme se già presente per
quel device dopo `lastTimestamp`.

### 5.3 Riassunto: come capire se c'è un allarme

Per ogni messaggio ricevuto, in ordine di priorità:

1. **Tipo del messaggio**: se è `#8` o `#9`, è stato spedito a causa di un allarme (verifica
   conferma sul byte 3 Contact Reason).
2. **Byte 4 Alarm/Status**: se uno dei bit 0–3 è settato → allarme device segnalato.
3. **Byte 3 Contact Reason**: bit 1 (Alarm) + bit 6 (DynLim) + bit 7 (DynLim2) confermano
   l'origine.
4. **Misure decodificate**: applica le `AlarmRule` server-side per coprire i casi non gestiti dal
   device (es. soglie diverse dalla nostra config, allarmi temperatura, batteria scarica).
5. **Assenza di messaggi**: il `MissedTransmissionDetector` segnala quando un device noto non
   trasmette da troppo tempo.

---

## 6. Comandi dal server al device

### 6.1 Forma canonica (XLSM `822 CC` R0002)

```
<Password>,<Settings>,<CRC><CRLF>
```

- `<Password>` è il valore del registro **S11** del device (default `TEK822`).
- `<Settings>` è la concatenazione comma-separated dei singoli registri/comandi.
- `<CRC>` è opzionale (di nuovo, polinomio non documentato — il nostro encoder non lo calcola).
- `<CRLF>` (`\r\n`) è il terminatore obbligatorio per indicare la fine del comando.

Esempio:
```
TEK822,S0=80,R3=ACTIVE\r\n
```

Sul cavo (TCP/GPRS) viaggiano i **byte ASCII** del comando, non la rappresentazione hex.

### 6.2 Concatenazione di più comandi

Se inviamo più comandi nello stesso payload, la **password va solo sul primo**. Tutti i comandi
successivi vengono "ripuliti" rimuovendo tutto fino alla prima virgola inclusa.

Esempio: i tre comandi logici
```
TEK822,S0=80
TEK822,S1=01
TEK822,R3=ACTIVE
```
diventano sul cavo:
```
TEK822,S0=80,S1=01,R3=ACTIVE\r\n
```

### 6.3 R-commands (richieste) — `Request Commands` sheet

| Comando | Significato | Risposta |
|---|---|---|
| `R1=01` | Send Logger data to server | Msg #4 |
| `R1=02` | Send S-Regs (from S0) | Msg #6 |
| `R1=04` | Send S-Regs (from S12) | Msg #6 (truncato se SMS) |
| `R1=08` | Send S-Regs (from S19) | Msg #6 |
| `R1=10` | Reset RTC | nessuna |
| `R1=20` | Send Buffer data | Msg #8 |
| `R1=40` | Initiate OTA firmware upgrade | RDY,0 |
| `R1=80` | Shutdown modem + sleep | nessuna |
| `R2=YY/MM/DD:hh/mm/ss` | Set RTC | nessuna |
| `R3=ACTIVE` | Attiva device | nessuna |
| `R4=DEACT` | Disattiva scheduled upload | nessuna |
| `R6=01` | Diagnostic data | Msg #2 |
| `R6=02` | Additional status | Msg #16 |
| `R6=03` | Close TCP | nessuna |
| `R7=FF` | Request GPS (FF = timeout hex secondi) | Msg #17 |

I comandi `R5=*` sono **solo transponder** (engineering on-site via TSP, non TCP) e non vengono
mai inviati dal nostro worker.

### 6.4 S-registers (configurazione) — `822 CC` sheet

Ogni `Sx=value` modifica un registro di configurazione. **Dopo aver inviato uno o più S-commands
si deve sempre concatenare `R3=ACTIVE`** per applicare le modifiche (il nostro encoder lo fa in
automatico).

Sintesi:

| Reg | Nome | Formula / Tipo |
|---|---|---|
| S0 | Logger Config | `(128 × samplingPeriod) + (loggerSpeedHours × 4)`, hex 1 byte |
| S1 | Listen Config | `listenMinutes / 5`, hex 1 byte |
| S2 | Schedule Config | 3 byte hex impacchettati: giorni della settimana, ora, frequenza |
| S3 | Control Configurator | bitmask (modalità rete, CRC enable, ecc.) |
| S4/S5/S6 | Static Limit (3 indipendenti) | `threshold + hyst × 2^10 + enabled × 2^14 + polarity × 2^15`, hex 2 byte |
| S7/S8 | Dynamic Limit | `polarity × 2^7 + enabled × 2^6 + rate`, hex 1 byte |
| S9 / S10 | SMS phone primario / secondario | ASCII max 18 char |
| S11 | Password unità | ASCII max 6 char (default `TEK822`) |
| S12 / S13 / S14 | APN / username / password | ASCII |
| S15 / S16 | IP server / porta | ASCII |
| S17 | Capacità batteria mAh | hex 2 byte (es. `1C20` = 7200 mAh) |
| S18 / S20 | F-Stop / E-Stop (calibrazione ratiometrica) | `(200 × volt) / 5`, hex |
| S19 | Control3 (APN auth) | bitmask |
| S21 | MCC_MNC operatore | ASCII (es. `27201` per Vodafone IE) |
| S22 | LTE Band | hex (vedi tabella PDF §3.20.12) |
| S23 | Retry config | `(tryCount − 1) + ((periodSec/10 − 1) × 8)`, hex 1 byte |
| S24 | Schedule delay | minuti, max 14, hex 1 byte |
| S26 | **Control2 (formato output sensore)** | bitmask **critico** per interpretare le misure |
| S29 | Control4 | bitmask (offset timer S7, long timeframe retry) |

Cfr. la sezione 3.20 del PDF per le formule complete con esempi.

---

## 7. Note operative e gotchas

### 7.1 CRC
- Polinomio **non documentato** da Tekelek. La validazione non è implementata.
- I 2 byte CRC sono comunque sempre presenti in coda al payload e vanno **esclusi** dalle
  decodifiche ASCII (Msg #6/#16/#17) per evitare di leggere byte non-ASCII come carattere.
- Per i comandi in uscita il CRC è opzionale; il nostro encoder non lo calcola.

### 7.2 Endianness
- I valori multi-byte numerici (es. message count byte 17-18, energy used byte 20-21) sono
  **big-endian**: `byte_alto × 256 + byte_basso`.

### 7.3 IMEI
- 8 byte BCD, 2 cifre per byte = 16 cifre. Il primo zero è un padding (IMEI è 15 cifre).
- Encoding fisso: `byte[7] = "08"`, `byte[8] = "64"`, ecc.

### 7.4 Battery percentage
- Le 5 cifre di precisione hanno una granularità di `100/31 ≈ 3.23%`. Non è un percentuale
  continua ma a step.

### 7.5 Logger Speed vs Sampling Period
- **Logger speed** (S0 bits[6:0] × 0.25 h): intervallo tra entry del *logger buffer* (28 slot,
  Msg #4).
- **Sampling period** (S0 bit 7, 0 = 1 min, 1 = 15 min): intervallo tra entry del *sampling
  buffer* (10 slot, Msg #8).

I due buffer sono indipendenti: il logger viene riempito secondo schedule lungo, il sampling
gira più veloce ed è quello che innesca gli allarmi dinamici.

### 7.6 Manual wakeup
Quando un operatore avvicina un magnete al "hot spot" del device:
1. Il device prende **10 letture in rapida sequenza** riempiendo il sampling buffer.
2. Invia un **Msg #8** con quel buffer.
3. Effetto collaterale: il sampling buffer rimane "azzerato" rispetto al normale flusso.

### 7.7 Riferimenti per dettagli
- Formule complete S-register: PDF §3.20, XLSM `822 CC`.
- Esempi di payload reali: XLSM sheet `822`, colonna `Message Examples` (righe 7–12).
- Mapping firmware-revisione/contact-reason/alarm-status: XLSM sheet `822` (R0011–R0027).
- Lookup tabella LTE Band: PDF §3.20.12.
- Lista comandi completa: XLSM sheet `Request Commands`.

---

## 8. Catalogo degli obisCode emessi dal decoder

Tabella sinottica di tutti i codici `Measure.obisCode` prodotti da `Tek822Decoder`, raggruppati
per tipo di messaggio. Utile come riferimento per query SQL su `device_measures`.

### 8.1 Common (per QUALSIASI tipo di messaggio)

Estratti da `decodeHeaderDiagnostics()` + flags da byte 3/4 in `Tek822Decoder.doDecode`,
**prima** del dispatcher per Msg type. Per Msg #4/#8/#9 il timestamp è ricostruito dal RTC del
device; per Msg #6/#16/#17 è il server time alla decodifica.

| obisCode | Tipo | Sorgente | Note |
|---|---|---|---|
| `header.product_type` | int | byte 0 | unit = label (es. `"TEK822 V2 NB"`) |
| `header.hw_revision` | double | byte 1 | display "minor.major" (es. `2.0`), unit = modem label (`"BG96"` o `"BG95"`) |
| `header.fw_revision` | double | byte 2 | display "major.minor" (es. `3.0`), unit = `"FW major.minor"` |
| `header.gsm_rssi` | int 0-31 | byte 5 | — |
| `header.battery_percent` | double 0-100 | byte 6 bits[4:0] | unit = `"% Capacity Remaining"`, 2 decimali |
| `header.rtc_set` | 0/1 | byte 6 bit 5 | — |
| `header.lte_active` | 0/1 | byte 6 bit 6 | 1 = LTE/CAT-M registrato, 0 = 2G |
| `contact_reason_flags` | int 0-255 | byte 3 | bitmask raw (decoder per label: `FlagDecoder.contactReasonFlags`) |
| `alarm_status_flags` | int 0-255 | byte 4 | bitmask raw (decoder per label: `FlagDecoder.alarmStatusFlags`) |

### 8.2 Aggiuntivi per Msg #4/#8/#9 (binary measures)

| obisCode | Tipo | Sorgente | Note |
|---|---|---|---|
| `header.message_count` | int | byte 17-18 BE | counter cumulativo del device |
| `header.try_tickets_remaining` | int 0-7 | byte 19 bits[7:5] | retry rimanenti per la trasmissione corrente |
| `header.energy_used_mah` | int | byte 20-21 BE | FW > 3.0 (legacy: era "last error code") |
| `network.tech_code` | 0/1/2 | byte 22 bit 7 + byte 6 bit 6 | unit = `"GSM"` / `"CATM"` / `"NB"` |
| `network.mnc` | int 0-15 | byte 22 bits[3:0] | network operator short code |
| `network.login_time_s` | int | byte 24 × 5 | secondi di connessione |
| `distance_cm` | int 0-1023 | byte (26+i·4)+2 bits[1:0] + byte +3 | **nome storico**, unità reale dipende da S26 |
| `temperature_c` | double | byte (26+i·4)+1 / 2 − 30 | range −30…+97.5 °C |
| `aux1` | int 0-15 | byte (26+i·4)+2 bits[5:2] | sanity check (atteso 10) |
| `aux2` | int 0-255 | byte (26+i·4) | sanity check (atteso 10) |

Per ogni slot non vuoto si emettono 4 measure con lo **stesso timestamp** = `baseTimestamp − i × loggerSpeed`.

### 8.3 Aggiuntivi per Msg #6 (settings dump)

Un `Measure` per **ogni** `Sx=value` trovato nel body ASCII (anche se vuoto o ASCII):

| obisCode | Tipo | Convenzione |
|---|---|---|
| `setting.S0` … `setting.S29` | double | `value` = `Long.parseLong(raw, 16)` se hex-parseable, `0.0` altrimenti. `unit` = stringa raw originale (es. `"81"`, `"iot.1nce.net"`, `""`) |

### 8.4 Aggiuntivi per Msg #16 (statistics)

Vedi §4.3 per il dettaglio. obisCode: `stats.iccid`, `stats.energy_used_ma_minutes`,
`stats.min_temperature_c`, `stats.max_temperature_c`, `stats.message_count`,
`stats.delivery_fail`, `stats.total_send_time_s`, `stats.max_send_time_s`,
`stats.min_send_time_s`, `stats.rssi_total`, `stats.rssi_valid_count`, `stats.rssi_fail_count`.

### 8.5 Aggiuntivi per Msg #17 (GPS)

Vedi §4.4 per il dettaglio. obisCode: `gps.time_to_fix_s`, `gps.utc`, `gps.latitude`,
`gps.longitude`, `gps.hdop`, `gps.altitude_m`, `gps.fix_mode`, `gps.heading_deg`,
`gps.speed_kmh`, `gps.speed_knots`, `gps.date`, `gps.satellites`.

### 8.6 Alarm codes prodotti

| alarmCode | Sorgente | Quando |
|---|---|---|
| `DEVICE_LIMIT_1` | byte 4 bit 0 | superamento soglia S4 (lato device) |
| `DEVICE_LIMIT_2` | byte 4 bit 1 | superamento soglia S5 |
| `DEVICE_LIMIT_3` | byte 4 bit 2 | superamento soglia S6 |
| `DEVICE_BUND_STATUS` | byte 4 bit 3 | switch hardware bund attivato |
| `TANK_LEVEL_LOW` | server-side AlarmRule | `distance_cm < alarm.tank.low_threshold` (config) |
| `TANK_LEVEL_HIGH` | server-side AlarmRule | `distance_cm > alarm.tank.high_threshold` |
| `BATTERY_LOW` | server-side AlarmRule | (richiede `battery_voltage` non ancora estratto) |
| `TEMPERATURE_OUT_OF_RANGE` | server-side AlarmRule | fuori `[min, max]` config |
| `MISSED_TRANSMISSION` | scheduler `MissedTransmissionDetector` | `now − lastTimestamp > soglia` (default 24h) |

---

## 9. Modulo `gas-telemetry-decode-tool`

Modulo Spring Boot creato come **ausilio di debugging**. Non fa parte della pipeline di produzione:
permette di incollare una stringa hex di payload TEK822 e visualizzare la decodifica completa
nel browser, con byte sorgente per ogni campo. Utile per troubleshooting on-the-fly e per il
confronto byte-per-byte rispetto allo sheet "822" dell'XLSM di riferimento.

### 9.1 Principio architetturale

**Decoder = protocol knowledge, decode-tool = solo ausilio**:
- Tutta la conoscenza di protocollo (formule, bit positions, mapping byte→label) vive nel modulo
  `gas-telemetry-decoder` (`Tek822Decoder`, `FlagDecoder`, `AlarmCodes`).
- Il decode-tool importa `Tek822Decoder` come dipendenza Maven (con `<exclusion>` su JPA /
  Event Hub / persistence per non tirare dentro tutte le transitive) e lo invoca con un
  `InMemoryDecoderContext` che cattura il `DecodedPacket` invece di pubblicarlo.

### 9.2 Endpoint REST

```
POST http://localhost:8095/api/decode
Content-Type: application/json

{ "hex": "180203418919360864431047987054047B..." }
```

Risposta JSON: `DecodeResponse` con `deviceId` (IMEI), `messageType`, `declaredBodyLength`,
`contactReasonFlags`/`alarmStatusFlags` (array di label da `FlagDecoder`), array `measures` e
`alarms`. Ogni measure/alarm è arricchito con `sourceHex` (byte hex sorgente) e `byteRange`
(formato `"N"` o `"N-M"`).

### 9.3 UI web

Pagina HTML statica a `http://localhost:8095/` con textarea per il payload + 4 tabelle di
risultato: Header / Allarmi auto-segnalati / Diagnostica header / Misure time-series. Ogni
tabella ha colonne `Hex` e `Byte` come prime due — match diretto con l'XLSM (cella H1 / A187).

### 9.4 Classi chiave

| Classe | Responsabilità |
|---|---|
| `DecodeToolApplication` | entry point Spring Boot, porta 8095 |
| `DecoderBeansConfig` | espone `Tek822Decoder` come `@Bean` (no component-scan del modulo decoder) |
| `service.PayloadParser` | hex → byte[], IMEI BCD raw |
| `service.InMemoryDecoderContext` | cattura il `DecodedPacket` |
| `service.HexSlicer` | mappa obisCode → (offset, length) per generare `sourceHex` + `byteRange` |
| `rest.DecodeController` | endpoint REST |
| `rest.DecodeRequest/Response/MeasureView/AlarmView` | DTO |
| `static/index.html` | UI single-page, fetch + render |

### 9.5 Test di "coperture" anti-regressione

- `HexSlicerCoverageTest` — verifica che ogni `Measure`/`Alarm` prodotto dal decoder abbia
  un mapping in `HexSlicer.FIXED_OFFSETS` (per le measure header) o in `slotMeasureSlice`
  (per le measure per-slot). Se domani qualcuno aggiunge una `Measure` nel decoder senza
  estendere il mapping, la build fallisce con l'elenco dei codici scoperti.
- `FlagDecoderTest` — pin del bit→label per Contact Reason e Alarm Status.
- `Tek822DecoderTest.realPayload_*` — test integrati su payload reali XLSM (Msg #4, #6, #16, #17),
  pattern da riusare per nuovi tipi di messaggio.
