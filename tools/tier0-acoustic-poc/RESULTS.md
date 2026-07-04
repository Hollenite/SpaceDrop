# Tier 0 Phase 1 — Measurement results

> **Status: PENDING MANUAL RUN** — POC tools are implemented; fill this file after running the protocol on physical hardware (Xiaomi + Acer).

Fill this in after running the protocol in [README.md](README.md).  
**Do not proceed to Phase 2 (FSK) until the exit gate row below is PASS.**

## Hardware under test

| Role | Device | Model | Notes |
|------|--------|-------|-------|
| Emitter (PC) | | e.g. Acer Aspire | Speaker placement: top / bottom / side |
| Listener (phone) | | e.g. Xiaomi 2311DRK48I | Android version |

**Test date:**  
**Tester:**  
**Room:** (size, flooring, quiet level)

---

## Step A — Speaker rolloff (sweep 17–21 kHz, 5 s each)

| Freq (Hz) | Max magnitude (rel) | SNR (dB) | Notes |
|-----------|---------------------|----------|-------|
| 17000 | | | |
| 17500 | | | |
| 18000 | | | |
| 18500 | | | |
| 19000 | | | |
| 19500 | | | |
| 20000 | | | |
| 20500 | | | |
| 21000 | | | |

**Peak usable frequency:**  
**Usable band ≥ 18 kHz?** YES / NO

---

## Step B — Range (best freq, burst mode, quiet room)

Frequency used: ______ Hz

| Distance (m) | Trials | Detections | Rate |
|--------------|--------|------------|------|
| 0.5 | 20 | | |
| 1.0 | 20 | | |
| 2.0 | 20 | | |
| 3.0 | 20 | | |
| 5.0 | 20 | | |

**Detection at ≥ 3 m?** YES / NO  
**2 m burst rate (target ≥ 90%):** ___%

---

## Step C — Angle / placement

| Condition | Detected? | SNR (dB) | Notes |
|-----------|-----------|----------|-------|
| Phone facing PC | | | |
| Phone back to PC | | | |
| Laptop lid closed | | | |
| Laptop elevated (speakers unobstructed) | | | |

---

## Step D — False positives (PC silent, 60 s listen)

| Metric | Result |
|--------|--------|
| False DETECTED count | |
| Pass (0 false)? | YES / NO |

---

## Step E — Wall (qualitative)

| Location | Same room SNR | Adjacent room SNR | Detection drops? |
|----------|---------------|-------------------|------------------|
| | | | YES / NO |

---

## Step F — Ambient noise

| Condition | Still detects? | Notes |
|-----------|----------------|-------|
| Normal speech | | |
| TV / music at low volume | | |

---

## Exit gate

| Criterion | Required | Result |
|-----------|----------|--------|
| Usable tone band on PC | 18–20 kHz likely | PASS / FAIL |
| Burst detection at 2 m | ≥ 90% (20 trials) | PASS / FAIL |
| False positives (60 s silent) | 0 | PASS / FAIL |
| Results documented | This file complete | PASS / FAIL |

**Overall Phase 1 gate:** **PASS / FAIL / PENDING**

---

## Gate decision (Phase 2)

If **PASS:** Proceed to Phase 2 — FSK token encode/decode library (see plan).  
If **FAIL:** Document fallback before any main-app work:

- [ ] External USB speaker on PC retest
- [ ] Lower band (16–17 kHz) retest — note audibility to humans
- [ ] Abandon Tier 0 acoustic; rely on Tier 1+2 only

**Decision notes:**

---

## Phase 2 issue (create only if PASS)

Title: `Tier 0 Phase 2 — FSK token encode/decode library`  
Link: _paste GitHub issue URL when created_
