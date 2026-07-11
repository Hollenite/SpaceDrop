---
name: spacedrop-tier0
description: >-
  SpaceDrop Tier 0 near-ultrasonic acoustic copresence discovery — gate discipline,
  project conventions, and fragile-area warnings. Use before any Tier 0 work: POC
  measurements, FSK library, app wiring, or fusion integration.
disable-model-invocation: true
---

# SpaceDrop Tier 0 — Acoustic Discovery

Tier 0 adds **room-scoped copresence** via PC-emitted near-ultrasonic FSK and phone-side Goertzel listening. It is a **weighted fusion input**, not a standalone discovery path.

## How to use

1. **Read this SKILL first** — conventions and gate rules below.
2. **Read [`tools/tier0-acoustic-poc/PROGRESS.md`](../../tools/tier0-acoustic-poc/PROGRESS.md) second** — current phase, step status, and decisions log.
3. Do not start Phase N+1 until Phase N exit criteria are recorded as **DONE** in PROGRESS.
4. Do not touch `android/app/`, `windows/SpaceDrop.Tray/`, or orbital UI until the relevant phase gate allows it.

---

## Project conventions to follow

### Protocol shape and constants

- **Data plane (Tier 2):** JSON line-delimited handshake — `PING` / `PONG`, then `FILE_META` + raw bytes + `FILE_ACK`. Port **58392**.
- **Shared token (dev placeholder):** `spacedrop-dev-token` — used until real pairing ships.
- **Constants locations (mirror across platforms):**
  - [`docs/protocol.md`](../../docs/protocol.md) — canonical spec (BLE UUIDs, mDNS TXT, fusion weights, port, token).
  - Android: `SpaceDropHandshakeClient.HANDSHAKE_PORT` / `DEV_TOKEN`, BLE service UUIDs in scanner code.
  - Windows: [`SpaceDropProtocol.cs`](../../windows/SpaceDrop.Tray/Protocol/SpaceDropProtocol.cs) — `Port`, `SharedToken`, `ServiceType`, GATT UUIDs.
- **Dedup key:** `device_id` (UTF-8 UUID) across BLE GATT, mDNS TXT, and (future) acoustic token hash.

### Coalescing and mutex discipline

When adding refresh-like work (including future acoustic polling), follow [`FusedNearbyDeviceRepository`](../../android/app/src/main/kotlin/com/prototype/airdrop/core/discovery/FusedNearbyDeviceRepository.kt):

- **`coalesceMutex`** — only one in-flight refresh; concurrent callers await the same `Deferred`.
- **`refreshMutex`** — serializes the actual merge/score work.
- Clear `inFlightRefresh` in `finally` only when that deferred completed.
- Do **not** stack duplicate periodic jobs; guard with `periodicJob?.isActive`.

### DEBUG gating for lab surfaces

New debug/diagnostic UI (e.g. Acoustic Lab) must be behind **`BuildConfig.DEBUG`**, same pattern as [`SettingsScreen.kt`](../../android/app/src/main/kotlin/com/prototype/airdrop/ui/screens/settings/SettingsScreen.kt) (BLE discovery lab, transfer lab). Never ship acoustic debug entry points in release builds.

### Fusion as weighted input — not a parallel path

Follow [`FullFusionScorer`](../../android/app/src/main/kotlin/com/prototype/airdrop/core/discovery/FullFusionScorer.kt) patterns:

- Acoustic contributes a **weight** added to BLE / mDNS / BSSID / host weights; cap confidence at **1.0**.
- **Never create a device row from acoustic alone.** Acoustic may only boost confidence for candidates already seen via BLE or mDNS. Prevents ghost PCs on the same LAN or through walls.
- Phase 4 weight rebalance is data-driven; do not hardcode acoustic as the dominant signal without measurement.

### Tier 0 POC isolation (Phase 1)

Standalone tools live under `tools/tier0-acoustic-poc/`. PC emits, phone listens. Goertzel detection math in [`shared/goertzel-notes.md`](../../tools/tier0-acoustic-poc/shared/goertzel-notes.md).

---

## Tier 0 gate discipline

Four phases, each with **checkable exit criteria**. Record completion in PROGRESS before advancing.

### Phase 1 — Hardware validation POC

**Scope:** `tools/tier0-acoustic-poc/` only. No main app or tray changes.

| Step | Exit criterion (all required for gate PASS) |
|------|---------------------------------------------|
| A. Speaker rolloff | Peak usable frequency identified; band ≥ 18 kHz likely on test PC |
| B. Range matrix | ≥ 90% burst detection at **2 m** (20 trials); detect at ≥ **3 m** |
| C. Placement | Orientation / lid / speaker placement documented |
| D. False positives | **0** false DETECTED in **60 s** with PC silent |
| E. Wall | Qualitative: detection weaker through wall vs same room |
| F. Ambient noise | Speech / TV at low volume — note pass/fail |
| Gate | All exit rows PASS in [`RESULTS.md`](../../tools/tier0-acoustic-poc/RESULTS.md); overall **PASS** declared |

**On FAIL:** follow [`GATE_DECISION.md`](../../tools/tier0-acoustic-poc/GATE_DECISION.md) fallbacks (elevate laptop → external USB speaker → lower band → abandon Tier 0). Do **not** open Phase 2.

### Phase 2 — FSK token encode/decode library

**Scope:** Shared Kotlin + C# library with unit tests from WAV fixtures. **No main-app wiring.**

| Exit criterion |
|----------------|
| Preamble (~300 ms sync tone) + 20-bit token + 4-bit CRC protocol implemented both sides |
| Binary FSK in 18.5–19.5 kHz band; symbol duration ≥ 80–100 ms |
| Token = `hash(device_id) mod 2^20`, rotated ~30 s |
| Decode accuracy gate passes on recorded fixtures (no live mic in CI) |
| GitHub issue created per GATE_DECISION template |

### Phase 3 — App wiring

**Scope:** Lifecycle-bound listener/emitter in main app + tray. **No fusion changes yet.**

| Exit criterion |
|----------------|
| PC `AcousticBeaconService` started alongside mDNS/BLE from tray host service |
| Android `AcousticCopresenceListener` started/stopped with Home/Share visibility (mirror periodic refresh lifecycle) |
| `RECORD_AUDIO` in main manifest + runtime permission flow |
| No always-on background mic service in v1 |
| Stable over manual Home ↔ Share navigation without leaked coroutine scopes |

### Phase 4 — Fusion integration

**Scope:** Discovery layer only — extend merger, breakdown, scorer, lab chip, `docs/protocol.md`.

| Exit criterion |
|----------------|
| `MergedDiscoveryCandidate` gains `hasAcoustic`, `acousticTokenMatch` |
| `FusionBreakdown` gains acoustic weight fields |
| `FullFusionScorer` rebalanced; acoustic weight applies only when token matches candidate `device_id` hash |
| BLE Discovery Lab shows Acoustic assist chip |
| `docs/protocol.md` Tier 0 section added |
| **Invariant verified:** acoustic alone never surfaces a new device row |

---

## Known fragile areas

Do not regress these when integrating Tier 0:

| Area | Risk | Mitigation |
|------|------|------------|
| **`inFlightAddresses`** (`BlePcScanner`) | Stale entries block GATT reads for an address | Always remove in GATT callback `finally`; clear on scan stop |
| **`AppState` unsync** | Share-flow globals (`pendingTransferItems`, `selectedDevice`) out of sync with navigation | Mirror existing clear-on-exit patterns in `AppNavGraph`; do not add parallel acoustic state here |
| **Uncancelled coroutine scopes** | Mic listener or refresh loops survive after screen exit | Bind to screen/repository lifecycle; cancel in `onCleared` / `stopPeriodicRefresh` |
| **`TrustState.PAIRED` placeholder** | Fusion scorer assigns PAIRED to all discovered PCs | Acoustic must not imply trust; pairing remains a separate future slice |

---

## Quick reference

| Artifact | Role |
|----------|------|
| [`tools/tier0-acoustic-poc/README.md`](../../tools/tier0-acoustic-poc/README.md) | POC run instructions + measurement protocol |
| [`tools/tier0-acoustic-poc/RESULTS.md`](../../tools/tier0-acoustic-poc/RESULTS.md) | Formal measurement log + gate verdict |
| [`tools/tier0-acoustic-poc/GATE_DECISION.md`](../../tools/tier0-acoustic-poc/GATE_DECISION.md) | Pass/fail next steps |
| [`docs/protocol.md`](../../docs/protocol.md) | Cross-platform constants and fusion weights |

**Current progress:** [`tools/tier0-acoustic-poc/PROGRESS.md`](../../tools/tier0-acoustic-poc/PROGRESS.md)
