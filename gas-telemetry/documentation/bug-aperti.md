# Bug aperti e TODO — gas-telemetry / TEK822

Elenco dei punti noti ancora aperti rispetto alla specifica TEK822 v1.21
(`configuration_and_command_v1.21.pdf` + `.xlsm`).

Vedi anche [protocollo-tekelek-tek822.md](protocollo-tekelek-tek822.md) per la descrizione
completa del protocollo.

> **Cosa è già stato fatto**: i fix applicati nel corso degli audit non sono elencati qui — la
> documentazione principale riflette già lo stato corrente del codice. Questo file traccia solo
> ciò che resta **aperto**.

---

## 1. `distance_cm` mal-nominato (impatto: medio, priorità: bassa)

Il valore a 10 bit dei 2 byte di ogni slot misura (Msg #4/#8/#9) **non è in cm in senso assoluto**.
La sua unità di misura reale dipende dal **registro S26** del device:

| S26 bit 7 | S26 bit 6 | Significato della misura |
|---|---|---|
| 1 | — | Raw ADC 0-1023 |
| 0 | 1 | Percentuale alta risoluzione 0-1000 |
| 0 | 0 | Percentuale bassa risoluzione 0-100 |

Per applicazioni LPG tank è tipicamente **percentuale** (0-100), non centimetri.

**Fix richiede**:
- leggere lo stato S26 di ciascun device (via Msg #6) e persistirlo lato server in
  `device_config`
- scalare la `distance` di conseguenza al momento della decodifica (o lasciare il valore raw
  e applicare la scalatura in lettura)
- rinominare l'obisCode in `sensor.raw` o `sensor.percent` a seconda

**Workaround attuale**: il nome `distance_cm` e l'unità `cm` restano nel codice e nella response
REST. Chi consuma `device_measures` deve sapere che il valore è "raw 10-bit" e applicare la
scalatura conoscendo S26.

---

## 2. Encoder — S-registers ✅ COMPLETATO

> Tutti i registri S documentati nella spec TEK822 v1.21 sono ora coperti.
> Ogni costante `CMD_SET_X` ha il proprio metodo `encodeSetX` in
> `gas-telemetry/worker/.../Tek822Encoder.java` con test corrispondente in
> `Tek822EncoderTest.java`.

Sintesi della copertura attuale: S0, S1, S2, S3, S4, S5, S6, S7, S8, S11,
S12-S14 (APN), S15-S16 (server), S17, S18, S19, S20, S21, S22, S23, S24,
S26, S29. Coperti tutti tranne S10 (Secondary Server SMS — non documentato
sufficientemente nello spec e poco utilizzato in pratica) e S25/S27/S28
("Spare", marcati Reserved nella spec).

**Note operative**:
- L'encoder fa auto-append di `R3=ACTIVE` (REBOOT) per tutti gli S-command:
  i registri S si applicano solo dopo reboot.
- Per i registri ASCII (S9, S11, S12-S15, S21) il valore passa as-is.
- Per i registri numerici (la maggioranza) il valore è codificato in
  esadecimale come da spec §3.20.
- S22 (LTE Band) supporta valori fino a B39 = `0xEE6B2800` (4 miliardi):
  internamente usa `long` perché supera `Integer.MAX_VALUE`.

**Restano fuori dall'encoder ma sono comunque possibili via REST**: l'endpoint
`command-api` potrebbe esporre direttamente i nuovi `CMD_SET_*` con i loro
parametri JSON, ma questo lavoro non è ancora stato fatto.

---

## 3. Hex/Byte mapping vuoto per body ASCII (impatto: cosmetico, priorità: bassa)

Nel decode-tool, le measure dei body ASCII (`setting.*`, `stats.*`, `gps.*`) non hanno
mapping in `HexSlicer.FIXED_OFFSETS` perché la loro **posizione varia** in base alla
lunghezza dei campi ASCII precedenti. Le colonne **Hex** e **Byte** della UI mostrano `—`
per queste righe.

**Fix richiede**: una passata che parsa il body ASCII tracciando l'offset corrente
per ogni campo. Fattibile ma non banale (occorre considerare la virgola separatrice e
la lunghezza variabile dei valori).

**Workaround attuale**: per i Msg #6/#16/#17 i campi ASCII restano senza tracciamento
byte-per-byte nel decode-tool. Le measure sono comunque decodificate correttamente.

---

## 4. CRC validation (impatto: integrità, priorità: PERMANENTEMENTE BLOCCATO)

Il **polinomio CRC del protocollo TEK822 non è documentato** da Tekelek e non viene
fornito su richiesta. Il decoder estrae i 2 byte di CRC trailer e li **logga** in debug,
ma **non valida** il checksum.

Si fa affidamento sull'integrità del layer TCP (checksum L4). Per UDP futuri payload
sarebbe un problema reale.

**Nessuna azione possibile** finché Tekelek non rilascia la spec del polinomio.

---

## 5. Battery voltage non estratto (impatto: minore, priorità: bassa)

L'`AlarmRule` `BATTERY_LOW` (server-side) richiede una `Measure` con obisCode
`battery_voltage`, ma il decoder oggi NON la produce. Solo `header.battery_percent`
(stima del firmware) è disponibile.

**Fix richiede**: il firmware TEK822 non sembra esporre voltage diretto via questi payload.
Possibile rimuovere la `BatteryLowRule` o ridefinirla sul `battery_percent`.

---

## Test di regressione per i fix futuri

Quando si chiude uno di questi bug, **aggiungere sempre un test integrato** sul payload
reale dell'XLSM e aggiornare questa lista (rimuovere la voce o spostarla in una sezione
"Fix recenti" della documentazione principale).

Pattern di riferimento: `Tek822DecoderTest.realPayload_xlsm*` per i payload binary,
`realPayload_msg6/16/17_xlsm*` per i body ASCII.
