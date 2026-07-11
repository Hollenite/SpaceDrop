# Tier 0 Phase 1 — Measurement results

> **Status: GATE PASS (2026-07-04)** — Phase 1 complete. **Phase 2 CI gate PASS (2026-07-04)** — fixture decode 100%; live @ 2 m manual pending.

**Phase 2 issue:** [GitHub #3](https://github.com/Hollenite/SpaceDrop/issues/3)

## Hardware under test

| Role | Device | Model | Notes |
|------|--------|-------|-------|
| Emitter (PC) | Acer laptop | Aspire (Realtek audio) | **Bottom-firing speakers** |
| Listener (phone) | Xiaomi | 2311DRK48I | Android 16; mic source `UNPROCESSED` |

**Test date:** 2026-07-04  
**Tester:** Ariyan  
**Room:** Home (desk ~0.5 m for Step A; quiet room for Steps B / D)

---

## Informal desk baseline (2026-07-04 — superseded by formal data below)

| Condition | SNR (dB) | DETECTED | Notes |
|-----------|----------|----------|-------|
| PC burst ON @ 19 kHz, ~desk distance | 50–60 | yes | Realtek speakers auto-selected |
| PC burst OFF | ~11 | no | Below 12 dB threshold |
| Earphone routing (pre-fix) | 39–60 | yes | Invalid for room copresence — fixed via `AudioOutputSelector` |

---

## Step A — Speaker rolloff (sweep 17–21 kHz, 5 s each)

**PC:** `dotnet run -- --mode sweep --duration 120` (~13:21)  
**Phone:** Slider at **19000 Hz**; **Reset peak** per step.

> **Methodology note:** Goertzel is single-bin. Only **19000 Hz** row is matched; other rows are in-bin leakage (qualitative). Peak @ 19 kHz is sufficient for gate.

| Freq (Hz) | Max magnitude (rel) | SNR (dB) | Notes |
|-----------|---------------------|----------|-------|
| 17000 | 1.06e-05 | 20.6 | Detuned |
| 17500 | 1.04e-05 | 20.4 | Detuned |
| 18000 | 9.90e-06 | 20.0 | Detuned |
| 18500 | 9.51e-06 | 19.6 | Detuned |
| 19000 | **7.07e-04** | **57.1** | **Matched** @ ~0.5 m |
| 19500 | 3.15e-05 | 30.1 | Detuned (+500 Hz) |
| 20000 | 8.08e-06 | 18.2 | Detuned |
| 20500 | 5.48e-06 | 14.9 | Detuned |
| 21000 | 7.16e-06 | 17.3 | Detuned |

**Peak usable frequency:** **19000 Hz**  
**Usable band ≥ 18 kHz?** **YES**

**Step A exit:** **PASS**

---

## Step B — Range (19000 Hz, burst mode, quiet room)

**PC:** `dotnet run -- --freq 19000 --mode burst --duration 60` (gain-gated 1 s ON / 2 s OFF; verified ~1000 ms ON via `.fff` logs)  
**Phone:** Manual **Hit/Miss** and/or **Burst auto test** (SNR ≥ 18 dB for 500 ms audio-block time; 2.2 s cooldown between hits)

Frequency used: **19000 Hz**

| Distance (m) | Trials | Detections | Rate | Method | Peak SNR (approx) |
|--------------|--------|------------|------|--------|-------------------|
| 0.5 | 20 | 20 | **100%** | Manual + logcat | 46–58 dB |
| 1.0 | 20 | 20 | **100%** | Manual | ~43 dB |
| 2.0 | 20 | 19 | **95%** | Burst auto test | 40–44 dB |
| 2.0 | 20 | 20 | **100%** | Manual (earlier run) | 40–44 dB |
| 3.0 | 20 | — | _not run_ | — | — |
| 5.0 | 20 | — | _not run_ | — | — |

**Detection at ≥ 3 m?** _Not measured (optional)_  
**2 m burst rate (gate, target ≥ 90%):** **95% (19/20 auto)** — **PASS**  
_(Manual 2 m run: 20/20; auto test is stricter engineering metric.)_

**Step B gate exit:** **PASS**

---

## Step C — Angle / placement

| Condition | Detected? | SNR (dB) | Notes |
|-----------|-----------|----------|-------|
| Phone facing PC | yes | 50–60 | Desk ~0.5 m (informal + Step A/B) |
| Phone back to PC | — | — | _Not run_ |
| Laptop lid closed | — | — | _Not run_ |
| Laptop elevated | — | — | _Not run_ |

---

## Step D — False positives (PC silent, 60 s listen)

**Phone:** **Start 60s test** (500 ms sustained DETECTED before counting FP)

| Metric | Result |
|--------|--------|
| False DETECTED count | **0** (quiet room, PC silent) |
| Pass (0 false)? | **YES** |

_Ad-hoc while speaking: brief DETECTED flickers (sibilants leak into 19 kHz bin). Not a gate failure — Step D requires quiet room. Production mitigated by FSK+CRC in Phase 2._

**Step D exit:** **PASS**

---

## Step E — Wall (qualitative)

| Location | Same room SNR | Adjacent room SNR | Detection drops? |
|----------|---------------|-------------------|------------------|
| — | — | — | _Not run_ |

---

## Step F — Ambient noise

| Condition | Still detects? | Notes |
|-----------|----------------|-------|
| Normal speech | spurious DETECTED | Brief SNR spikes; 500 ms hold reduces false counts in silent test |
| TV / music at low volume | — | _Not run_ |
| Pressure cooker / kitchen steam | spurious DETECTED | Broadband/hiss can energize 19 kHz bin; Phase 2 token required |

---

## POC app issues observed (non-gate)

| Issue | Notes |
|-------|-------|
| Stuck high SNR after Start listening | Intermittent; noise floor may stop adapting while magnitude stays elevated → persistent high SNR / DETECTED. POC-only; fix before Phase 3 app wiring if reproduced. |
| Burst auto test v1 | Schedule sync + cooldown bugs → fixed with gain-gated emitter + block-accum + wall-clock cooldown. |

---

## Exit gate

| Criterion | Required | Result |
|-----------|----------|--------|
| Usable tone band on PC | 18–20 kHz likely | **PASS** (19 kHz @ 57 dB matched) |
| Burst detection at 2 m | ≥ 90% (20 trials) | **PASS** (19/20 auto = 95%) |
| False positives (60 s silent) | 0 | **PASS** |
| Results documented | This file complete | **PASS** (gate rows; optional 3/5 m / C / E deferred) |

**Overall Phase 1 gate:** **PASS**

---

## Gate decision (Phase 2)

**Proceed to Phase 2** — FSK token encode/decode library on this hardware pair.

Fallbacks **not required:**

- [ ] External USB speaker on PC retest
- [ ] Lower band (16–17 kHz) retest
- [ ] Abandon Tier 0 acoustic

**Decision notes:**

- 2026-07-04: Informal + formal validation on **Acer Realtek + Xiaomi `UNPROCESSED`**. Peak **19000 Hz**, SNR **57 dB** @ 0.5 m matched sweep.
- 2026-07-04: Range **0.5 / 1.0 / 2.0 m** — 100% / 100% / **95% auto (19/20)** @ 2 m; manual 2 m **20/20**. Bottom-firing speakers adequate to **2 m** in home room.
- 2026-07-04: Step D **0 FP** quiet room. Speech / pressure cooker confirm single-tone POC is not production-safe → Phase 2 FSK+CRC.
- 2026-07-04: Windows emitter **gain-gating** fix (was Stop/Play truncating tones). **Overall Phase 1 gate PASS.**

---

## Phase 2 — FSK library (automated + manual)

**GitHub issue:** [#3](https://github.com/Hollenite/SpaceDrop/issues/3)  
**Spec:** [shared/fsk-protocol.md](shared/fsk-protocol.md)

### Automated CI gate (2026-07-04)

| Check | Result |
|-------|--------|
| Kotlin `:fsk-core:test` round-trip | **PASS** (6/6) |
| Kotlin fixture decode (C# WAV → Kotlin decoder) | **PASS** (3/3 clean, 0/1 bad-crc) |
| C# `SpaceDrop.AcousticFsk.Tests` | **PASS** (8/8) |
| C# encode → Kotlin decode parity | **PASS** |
| Scope: no `android/app/` or `SpaceDrop.Tray/` edits | **PASS** |

### Manual live gate (pending user session)

Run on **Acer Realtek + Xiaomi 2311DRK48I** (same pair as Phase 1):

**PC:**

```powershell
cd tools\tier0-acoustic-poc\windows\AcousticEmitter
dotnet run -- --mode fsk --device-id 550e8400-e29b-41d4-a716-446655440000 --epoch 100 --duration 120
```

**Phone:** POC app → **Start listening** → **FSK decode** section → tap **Refresh epoch** if not using `--epoch 100`.

| Distance | Trials | Pass criterion | Result |
|----------|--------|----------------|--------|
| 0.5 m | 5 frames | Token match within 2 frames | _pending_ |
| 2.0 m | 20 frames | ≥ 90% token match | _pending_ |
| PC silent 60 s | — | 0 valid token decodes | _pending_ |

**18.5 / 19.5 kHz smoke (informal):**

```powershell
dotnet run -- --freq 18500 --mode continuous --duration 10
dotnet run -- --freq 19500 --mode continuous --duration 10
```

Compare SNR to 19 kHz baseline in POC magnitude bar; note in this table when run.

| Freq (Hz) | SNR @ 0.5 m (dB) | Notes |
|-----------|------------------|-------|
| 18500 | _pending_ | |
| 19000 | 57.1 (Phase 1) | baseline |
| 19500 | _pending_ | |

**Phase 2 overall gate:** **CI PASS** — manual live rows above must be filled before Phase 3 wiring.

---

## Phase 2 issue (closed when manual gate filled)

Title: `Tier 0 Phase 2 — FSK token encode/decode library`  
Link: https://github.com/Hollenite/SpaceDrop/issues/3
